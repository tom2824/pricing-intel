package io.github.tom2824.pricingintel.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tom2824.pricingintel.domain.Listing;
import io.github.tom2824.pricingintel.domain.ListingId;
import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import io.github.tom2824.pricingintel.domain.ProductId;
import io.github.tom2824.pricingintel.domain.SourceId;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollectionRunTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneOffset.UTC);

    private final List<PriceSnapshot> received = new ArrayList<>();
    private final PriceSink recordingSink = received::add;

    @Test
    void collectsEachListingWithTheFirstSupportingSource() {
        Listing a = listing("a", "https://shop-a.test/p/1");
        Listing b = listing("b", "https://shop-b.test/p/2");
        PriceSource sourceA = fixedPrice("src-a", "shop-a.test", "10.00");
        PriceSource sourceB = fixedPrice("src-b", "shop-b.test", "20.00");

        CollectionReport report = new CollectionRun(List.of(sourceA, sourceB), recordingSink, () -> List.of(a, b), CLOCK).run();

        assertThat(report.attempted()).isEqualTo(2);
        assertThat(report.collected()).isEqualTo(2);
        assertThat(report.hasFailures()).isFalse();
        assertThat(received).extracting(s -> s.price().amount().toPlainString()).containsExactly("10.00", "20.00");
    }

    @Test
    void reportsListingsNoSourceSupports() {
        Listing orphan = listing("orphan", "https://unknown.test/p/9");

        CollectionReport report = new CollectionRun(List.of(fixedPrice("src", "shop.test", "1")), recordingSink,
                () -> List.of(orphan), CLOCK).run();

        assertThat(report.collected()).isZero();
        assertThat(report.isTotalFailure()).isTrue();
        assertThat(report.failures()).singleElement().satisfies(f -> {
            assertThat(f.listingId()).isEqualTo(new ListingId("orphan"));
            assertThat(f.sourceId()).isEqualTo("none");
            assertThat(f.retryable()).isFalse();
        });
    }

    @Test
    void keepsGoingAfterAnObservationFailure() {
        Listing failing = listing("failing", "https://shop.test/p/1");
        Listing ok = listing("ok", "https://shop.test/p/2");
        PriceSource flaky = new PriceSource() {
            @Override
            public String id() {
                return "flaky";
            }

            @Override
            public boolean supports(Listing listing) {
                return true;
            }

            @Override
            public PriceSnapshot observe(Listing listing) throws ObservationException {
                if (listing.id().value().equals("failing")) {
                    throw new ObservationException("HTTP 503", true);
                }
                return snapshot(listing, "5.00");
            }
        };

        CollectionReport report = new CollectionRun(List.of(flaky), recordingSink, () -> List.of(failing, ok), CLOCK).run();

        assertThat(report.collected()).isEqualTo(1);
        assertThat(report.failures()).singleElement().satisfies(f -> {
            assertThat(f.reason()).isEqualTo("HTTP 503");
            assertThat(f.retryable()).isTrue();
        });
    }

    @Test
    void sinkFailureAbortsTheRun() {
        PriceSink broken = snapshot -> {
            throw new IllegalStateException("disk full");
        };

        CollectionRun run = new CollectionRun(List.of(fixedPrice("src", "shop.test", "1")), broken,
                () -> List.of(listing("a", "https://shop.test/p/1")), CLOCK);

        assertThatThrownBy(run::run).isInstanceOf(IllegalStateException.class).hasMessage("disk full");
    }

    private static Listing listing(String id, String url) {
        return new Listing(new ListingId(id), new ProductId("p-" + id), new SourceId("s"), URI.create(url));
    }

    private static PriceSnapshot snapshot(Listing listing, String price) {
        return PriceSnapshot.builder(listing.id(), CLOCK.instant(), listing.url(), Money.eur(price))
                .extraction("test", 1.0)
                .build();
    }

    private static PriceSource fixedPrice(String id, String host, String price) {
        return new PriceSource() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean supports(Listing listing) {
                return listing.host().equals(host);
            }

            @Override
            public PriceSnapshot observe(Listing listing) {
                return snapshot(listing, price);
            }
        };
    }
}
