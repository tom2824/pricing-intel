package io.github.tom2824.pricingintel.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tom2824.pricingintel.collector.CollectionReport;
import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.Listing;
import io.github.tom2824.pricingintel.domain.ListingId;
import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.ObservedIdentity;
import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Un vrai PostgreSQL embarqué, les vraies migrations Flyway, et le cycle complet : import du catalogue,
 * annonces fournies au collecteur, relevés écrits avec quarantaine, échecs stockés.
 * Les tests s'enchaînent sur la même base, dans l'ordre.
 */
@SpringBootTest
@ActiveProfiles("postgres")
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersistenceIntegrationTest {

    private static final Instant DAY1 = PersistenceTestApplication.NOW;

    @Autowired
    CatalogueImporter importer;

    @Autowired
    PostgresListingProvider listingProvider;

    @Autowired
    PostgresPriceSink priceSink;

    @Autowired
    PostgresCollectionReportSink reportSink;

    @Autowired
    JdbcClient jdbc;

    @Test
    @Order(1)
    void migrationsSeedFamiliesAndSources() {
        assertThat(jdbc.sql("select count(*) from product_family").query(Long.class).single()).isEqualTo(4L);
        assertThat(jdbc.sql("select count(*) from source").query(Long.class).single()).isEqualTo(4L);
    }

    @Test
    @Order(2)
    void importsTheCatalogueIdempotently() {
        CatalogueImporter.Result first = importer.importFile(Path.of("src/test/resources/catalogue-test.yml"));

        assertThat(first.productsCreated()).isEqualTo(4);
        assertThat(first.listingsCreated()).isEqualTo(4);
        assertThat(first.matchesCreated()).isEqualTo(3);

        CatalogueImporter.Result second = importer.importFile(Path.of("src/test/resources/catalogue-test.yml"));

        assertThat(second.productsCreated()).isZero();
        assertThat(second.productsUpdated()).isEqualTo(4);
        assertThat(second.listingsUpdated()).isEqualTo(4);
        assertThat(second.matchesCreated()).isZero();

        assertThat(jdbc.sql("select equivalence_key from product where brand = 'MSI'").query(String.class).single())
                .isEqualTo("gpu|chipset=rtx4070super|vram_gb=12");
        assertThat(jdbc.sql("select count(distinct equivalence_key) from product where family_code = 'gpu'").query(Long.class).single())
                .isEqualTo(1L);
        assertThat(jdbc.sql("select value from product_identifier where scheme = 'gtin' order by value").query(String.class).list())
                .containsExactly("0012345678905", "4711377114363");
        assertThat(jdbc.sql("select kind from source where code = 'newshop'").query(String.class).single()).isEqualTo("scraper");
    }

    @Test
    @Order(3)
    void refusesAnIdentifierThatBelongsToAnotherProduct() {
        CatalogueFile conflicting = new CatalogueFile(List.of(new CatalogueFile.ProductSpec("other", "gpu", "ASUS",
                "DUAL-RTX4070S-O12G", "ASUS Dual RTX 4070 SUPER", Map.of("chipset", "RTX 4070 SUPER", "vram_gb", 12),
                List.of(new CatalogueFile.IdentifierSpec("gtin", "4711377114363")), null, null, null)), List.of());

        assertThatThrownBy(() -> importer.importCatalogue(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already belongs");
        assertThat(jdbc.sql("select count(*) from product where brand = 'ASUS'").query(Long.class).single()).isZero();
    }

    @Test
    @Order(4)
    void providesActiveListingsWithTheirCurrentProduct() {
        List<Listing> listings = listingProvider.listings();

        assertThat(listings).extracting(l -> l.id().value())
                .containsExactly("ldlc-rtx4070s-gigabyte", "ldlc-rtx4070s-msi", "newshop-orphan", "topachat-rtx4070s-msi");
        Listing msi = listings.stream().filter(l -> l.id().value().equals("ldlc-rtx4070s-msi")).findFirst().orElseThrow();
        Listing topachat = listings.stream().filter(l -> l.id().value().equals("topachat-rtx4070s-msi")).findFirst().orElseThrow();
        Listing orphan = listings.stream().filter(l -> l.id().value().equals("newshop-orphan")).findFirst().orElseThrow();
        assertThat(msi.productId()).isEqualTo(topachat.productId());
        assertThat(msi.sourceId().value()).isEqualTo("ldlc");
        assertThat(orphan.productId().value()).isEqualTo(PostgresListingProvider.UNMATCHED);
    }

    @Test
    @Order(5)
    void upsertsOneSnapshotPerListingAndDay() {
        priceSink.accept(snapshot("ldlc-rtx4070s-msi", DAY1, "629.95"));
        priceSink.accept(snapshot("ldlc-rtx4070s-msi", DAY1.plus(Duration.ofHours(3)), "619.95"));

        assertThat(jdbc.sql("select count(*) from price_snapshot").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("select price from price_snapshot").query(BigDecimal.class).single()).isEqualByComparingTo("619.95");
        assertThat(jdbc.sql("select observed_gtin from price_snapshot").query(String.class).single()).isEqualTo("4711377114363");
    }

    @Test
    @Order(6)
    void quarantinesOutliersAndConfirmsOrRejectsThemWithTheNextSnapshot() {
        String confirmed = "ldlc-rtx4070s-msi";
        priceSink.accept(snapshot(confirmed, DAY1.plus(Duration.ofDays(1)), "615.00"));
        priceSink.accept(snapshot(confirmed, DAY1.plus(Duration.ofDays(2)), "199.00"));
        priceSink.accept(snapshot(confirmed, DAY1.plus(Duration.ofDays(3)), "205.00"));
        assertThat(quarantines(confirmed)).containsExactly("none", "none", "confirmed", "none");

        String rejected = "topachat-rtx4070s-msi";
        priceSink.accept(snapshot(rejected, DAY1, "640.00"));
        priceSink.accept(snapshot(rejected, DAY1.plus(Duration.ofDays(1)), "64.00"));
        priceSink.accept(snapshot(rejected, DAY1.plus(Duration.ofDays(2)), "639.00"));
        assertThat(quarantines(rejected)).containsExactly("none", "rejected", "none");

        String stillSuspect = "ldlc-rtx4070s-gigabyte";
        priceSink.accept(snapshot(stillSuspect, DAY1, "600.00"));
        priceSink.accept(snapshot(stillSuspect, DAY1.plus(Duration.ofDays(1)), "60.00"));
        priceSink.accept(snapshot(stillSuspect, DAY1.plus(Duration.ofDays(2)), "6000.00"));
        assertThat(quarantines(stillSuspect)).containsExactly("none", "suspect", "suspect");
    }

    @Test
    @Order(7)
    void refusesSnapshotsOfUnknownListings() {
        assertThatThrownBy(() -> priceSink.accept(snapshot("ghost", DAY1, "1.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    @Order(8)
    void storesCollectionRunsAndFailures() {
        CollectionReport report = new CollectionReport(DAY1, DAY1.plus(Duration.ofMinutes(2)), 4, 3, List.of(
                new CollectionReport.Failure(new ListingId("newshop-orphan"), "scraper", "HTTP 503", true),
                new CollectionReport.Failure(new ListingId("deleted-listing"), "none", "No source", false)));

        reportSink.accept(report);

        assertThat(jdbc.sql("select failed from collection_run").query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("select listing_id from collection_failure where listing_code = 'newshop-orphan'").query(Long.class).single())
                .isNotNull();
        assertThat(jdbc.sql("select listing_id from collection_failure where listing_code = 'deleted-listing'").query(Long.class).optional())
                .isEmpty();
    }

    private static PriceSnapshot snapshot(String listing, Instant at, String price) {
        return PriceSnapshot.builder(new ListingId(listing), at, URI.create("https://shop.test/" + listing), Money.eur(price))
                .availability(Availability.IN_STOCK)
                .identity(new ObservedIdentity("4711377114363", "MSI", null, null, "MSI RTX 4070 SUPER"))
                .extraction("jsonld", 0.95)
                .build();
    }

    private List<String> quarantines(String listing) {
        return jdbc.sql("select s.quarantine from price_snapshot s join listing l on l.id = s.listing_id"
                        + " where l.code = :code order by s.observed_date")
                .param("code", listing)
                .query(String.class).list();
    }
}
