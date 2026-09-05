package io.github.tom2824.pricingintel.sink.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.domain.ListingId;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DistilledSnapshotStoreTest {

    private static final Instant FETCHED = Instant.parse("2026-09-05T08:15:30Z");

    private static FetchResult page(Instant fetchedAt) {
        return new FetchResult(URI.create("https://shop.test/p/1"), URI.create("https://www.shop.test/fiche/1"),
                200, "text/html; charset=utf-8", HtmlDistillerTest.fixture("product-page.html"), fetchedAt);
    }

    @Test
    void writesOneGzippedJsonDocumentPerSnapshot(@TempDir Path root) throws IOException {
        new DistilledSnapshotStore(root, Duration.ofDays(180)).store(new ListingId("ldlc-rtx"), page(FETCHED));

        Path file = root.resolve("ldlc-rtx").resolve("20260905T081530Z.distilled.json.gz");
        assertThat(file).exists();

        JsonNode json;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            json = new ObjectMapper().readTree(in);
        }
        assertThat(json.get("listingId").asText()).isEqualTo("ldlc-rtx");
        assertThat(json.get("finalUri").asText()).isEqualTo("https://www.shop.test/fiche/1");
        assertThat(json.get("status").asInt()).isEqualTo(200);
        assertThat(json.get("title").asText()).startsWith("MSI GeForce");
        assertThat(json.get("jsonBlocks")).hasSize(4);
        assertThat(json.get("jsonBlocks").get(0).get("source").asText()).isEqualTo("ld+json");
        assertThat(json.get("jsonBlocks").get(0).get("content").get("gtin13").asText()).isEqualTo("4711377114363");
        assertThat(json.get("markdown").asText()).contains("# MSI GeForce RTX 4070 SUPER", "629,95 €");
        assertThat(json.get("htmlLength").asInt()).isGreaterThan(json.get("markdown").asText().length());
    }

    @Test
    void purgesOnlyItsOwnFilesOlderThanTheRetention(@TempDir Path root) throws IOException {
        DistilledSnapshotStore store = new DistilledSnapshotStore(root, Duration.ofDays(30));
        ListingId listing = new ListingId("l");
        store.store(listing, page(FETCHED.minus(Duration.ofDays(40))));
        store.store(listing, page(FETCHED.minus(Duration.ofDays(10))));
        Path foreign = root.resolve("l").resolve("20260101T000000Z.html.gz");
        Files.writeString(foreign, "not mine");

        int purged = store.purgeExpired(FETCHED);

        assertThat(purged).isEqualTo(1);
        assertThat(root.resolve("l").resolve("20260726T081530Z.distilled.json.gz")).doesNotExist();
        assertThat(root.resolve("l").resolve("20260826T081530Z.distilled.json.gz")).exists();
        assertThat(foreign).exists();
    }

    @Test
    void removesEmptyListingDirectoriesAndNeverPurgesWithoutRetention(@TempDir Path root) {
        DistilledSnapshotStore forever = new DistilledSnapshotStore(root, null);
        forever.store(new ListingId("old"), page(FETCHED.minus(Duration.ofDays(400))));
        assertThat(forever.purgeExpired(FETCHED)).isZero();

        DistilledSnapshotStore shortLived = new DistilledSnapshotStore(root, Duration.ofDays(1));
        assertThat(shortLived.purgeExpired(FETCHED)).isEqualTo(1);
        assertThat(root.resolve("old")).doesNotExist();
    }
}
