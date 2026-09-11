package io.github.tom2824.pricingintel.collector;

import static org.assertj.core.api.Assertions.assertThat;

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
    void recordsASinkFailureAsRetryableAndKeepsGoing() {
        PriceSink flakySink = snapshot -> {
            if (snapshot.listingId().value().equals("a")) {
                throw new IllegalStateException("connection reset");
            }
            received.add(snapshot);
        };
        List<Listing> listings = List.of(listing("a", "https://shop.test/p/1"), listing("b", "https://shop.test/p/2"));

        CollectionReport report = new CollectionRun(List.of(fixedPrice("src", "shop.test", "1")), flakySink, () -> listings, CLOCK).run();

        assertThat(report.collected()).isEqualTo(1);
        assertThat(received).hasSize(1);
        assertThat(report.failures()).singleElement().satisfies(f -> {
            assertThat(f.listingId()).isEqualTo(new ListingId("a"));
            assertThat(f.sourceId()).isEqualTo(CollectionRun.SINK);
            assertThat(f.reason()).contains("connection reset");
            assertThat(f.retryable()).isTrue();
        });
    }

    @Test
    void stopsFetchingWhenTheSinkKeepsFailingButStillReports() {
        PriceSink broken = snapshot -> {
            throw new IllegalStateException("disk full");
        };
        List<Listing> listings = List.of(listing("a", "https://shop.test/p/1"), listing("b", "https://shop.test/p/2"),
                listing("c", "https://shop.test/p/3"), listing("d", "https://shop.test/p/4"), listing("e", "https://shop.test/p/5"));
        List<String> fetched = new ArrayList<>();
        PriceSource counting = new PriceSource() {
            @Override
            public String id() {
                return "src";
            }

            @Override
            public boolean supports(Listing listing) {
                return true;
            }

            @Override
            public PriceSnapshot observe(Listing listing) {
                fetched.add(listing.id().value());
                return snapshot(listing, "1");
            }
        };

        CollectionReport report = new CollectionRun(List.of(counting), broken, () -> listings, CLOCK).run();

        assertThat(fetched).containsExactly("a", "b", "c");
        assertThat(report.attempted()).isEqualTo(5);
        assertThat(report.collected()).isZero();
        assertThat(report.isTotalFailure()).isTrue();
        assertThat(report.failures()).hasSize(4);
        assertThat(report.failures().get(3).reason()).contains("interrompue").contains("2 annonce(s) non tentée(s)");
    }

    @Test
    void skipsListingsAlreadyCollectedTodayAndCountsThemAsCollected() {
        Listing a = listing("a", "https://shop.test/p/1");
        Listing b = listing("b", "https://shop.test/p/2");
        ListingProvider provider = new ListingProvider() {
            @Override
            public List<Listing> listings() {
                return List.of(a, b);
            }

            @Override
            public java.util.Set<ListingId> collectedOn(java.time.LocalDate day) {
                assertThat(day).isEqualTo(java.time.LocalDate.of(2026, 9, 5));
                return java.util.Set.of(a.id());
            }
        };

        CollectionReport report = new CollectionRun(List.of(fixedPrice("src", "shop.test", "1")), recordingSink, provider, CLOCK).run();

        assertThat(received).extracting(s -> s.listingId().value()).containsExactly("b");
        assertThat(report.attempted()).isEqualTo(2);
        assertThat(report.collected()).isEqualTo(2);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.summary()).startsWith("2/2 relevés collectés (dont 1 déjà relevés aujourd'hui)");
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
