package io.github.tom2824.pricingintel.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tom2824.pricingintel.collector.FetchException;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.collector.ObservationException;
import io.github.tom2824.pricingintel.collector.PageFetcher;
import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.Listing;
import io.github.tom2824.pricingintel.domain.ListingId;
import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import io.github.tom2824.pricingintel.domain.ProductId;
import io.github.tom2824.pricingintel.domain.SourceId;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScraperPriceSourceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T08:00:00Z");

    private final SiteRegistry registry = SiteRegistry.strict(
            List.of(new SiteDefinitionLoader().parse(Fixtures.read("sites/shop-test.yml"))));
    private final List<ListingId> archived = new ArrayList<>();

    private ScraperPriceSource source(PageFetcher fetcher) {
        return new ScraperPriceSource(registry, fetcher, (id, result) -> archived.add(id), "EUR");
    }

    private static PageFetcher serving(int status, String page) {
        return uri -> new FetchResult(uri, uri, status, "text/html; charset=utf-8", Fixtures.read("pages/" + page), NOW);
    }

    private static Listing listing(String id, String url) {
        return new Listing(new ListingId(id), new ProductId("p"), new SourceId("shop-test"), URI.create(url));
    }

    @Test
    void supportsOnlyHttpUrlsOfKnownHosts() {
        ScraperPriceSource source = source(serving(200, "jsonld-product.html"));

        assertThat(source.supports(listing("a", "https://www.shop.test/p/1"))).isTrue();
        assertThat(source.supports(listing("b", "https://unknown.test/p/1"))).isFalse();
        assertThat(source.supports(listing("c", "ftp://www.shop.test/p/1"))).isFalse();
    }

    @Test
    void usesTheFirstExtractorThatFindsAPriceAndArchivesTheRawPage() throws ObservationException {
        Listing listing = listing("rtx", "https://www.shop.test/fiche/rtx-4070-super");

        PriceSnapshot snapshot = source(serving(200, "jsonld-product.html")).observe(listing);

        assertThat(snapshot.listingId()).isEqualTo(listing.id());
        assertThat(snapshot.observedAt()).isEqualTo(NOW);
        assertThat(snapshot.price().amount()).isEqualByComparingTo("629.95");
        assertThat(snapshot.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(snapshot.identity().gtin()).isEqualTo("4711377114363");
        assertThat(snapshot.extraction().method()).isEqualTo("jsonld");
        assertThat(archived).containsExactly(listing.id());
    }

    @Test
    void fallsBackAlongTheChain() throws ObservationException {
        PriceSnapshot fromNext = source(serving(200, "embedded-next.html")).observe(listing("ram", "https://www.shop.test/p/ram"));
        PriceSnapshot fromCss = source(serving(200, "css-product.html")).observe(listing("cpu", "https://www.shop.test/p/cpu"));

        assertThat(fromNext.extraction().method()).isEqualTo("embedded-json");
        assertThat(fromNext.price().amount()).isEqualByComparingTo("114.90");
        assertThat(fromNext.isDiscounted()).isTrue();
        assertThat(fromCss.extraction().method()).isEqualTo("css");
        assertThat(fromCss.price().amount()).isEqualByComparingTo("1299.00");
        assertThat(fromCss.listPrice().amount()).isEqualByComparingTo("1349.00");
    }

    @Test
    void failsWithoutRetryWhenNoExtractorFindsAPrice() {
        assertThatThrownBy(() -> source(serving(200, "no-price.html")).observe(listing("x", "https://www.shop.test/p/x")))
                .isInstanceOfSatisfying(ObservationException.class, e -> {
                    assertThat(e.getMessage()).contains("shop-test");
                    assertThat(e.isRetryable()).isFalse();
                });
    }

    @Test
    void mapsHttpStatusesToRetryability() {
        assertThatThrownBy(() -> source(serving(404, "no-price.html")).observe(listing("x", "https://www.shop.test/p/x")))
                .isInstanceOfSatisfying(ObservationException.class, e -> assertThat(e.isRetryable()).isFalse());
        assertThatThrownBy(() -> source(serving(503, "no-price.html")).observe(listing("x", "https://www.shop.test/p/x")))
                .isInstanceOfSatisfying(ObservationException.class, e -> assertThat(e.isRetryable()).isTrue());
        assertThat(archived).hasSize(2);
    }

    @Test
    void propagatesFetchFailuresWithTheirRetryability() {
        PageFetcher blocked = uri -> {
            throw new FetchException("Blocked by robots.txt", false);
        };

        assertThatThrownBy(() -> source(blocked).observe(listing("x", "https://www.shop.test/p/x")))
                .isInstanceOfSatisfying(ObservationException.class, e -> {
                    assertThat(e.getMessage()).contains("robots.txt");
                    assertThat(e.isRetryable()).isFalse();
                });
        assertThat(archived).isEmpty();
    }

    @Test
    void doesNotLetAnArchivingErrorBreakTheCollection() throws ObservationException {
        ScraperPriceSource source = new ScraperPriceSource(registry, serving(200, "jsonld-product.html"),
                (id, result) -> {
                    throw new IllegalStateException("disk full");
                }, "EUR");

        assertThat(source.observe(listing("rtx", "https://www.shop.test/p/rtx")).price().amount()).isEqualByComparingTo("629.95");
    }
}
