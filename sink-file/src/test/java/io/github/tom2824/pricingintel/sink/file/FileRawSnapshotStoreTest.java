package io.github.tom2824.pricingintel.sink.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.domain.ListingId;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRawSnapshotStoreTest {

    private static final Instant FETCHED = Instant.parse("2026-09-05T08:15:30Z");

    private static FetchResult result(Instant fetchedAt) {
        return new FetchResult(URI.create("https://shop.test/p/1"), URI.create("https://www.shop.test/fiche/1"),
                200, "text/html; charset=utf-8", "<html>Prix : 1 299,99 €</html>", fetchedAt);
    }

    @Test
    void writesGzippedBodyAndMetadataPerListingAndTimestamp(@TempDir Path root) throws IOException {
        new FileRawSnapshotStore(root).store(new ListingId("ldlc/rtx 4070"), result(FETCHED));

        Path dir = root.resolve("ldlc_rtx_4070");
        Path html = dir.resolve("20260905T081530Z.html.gz");
        Path meta = dir.resolve("20260905T081530Z.meta.json");
        assertThat(html).exists();
        assertThat(meta).exists();

        try (InputStream in = new GZIPInputStream(Files.newInputStream(html))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("<html>Prix : 1 299,99 €</html>");
        }
        JsonNode json = new ObjectMapper().readTree(meta.toFile());
        assertThat(json.get("status").asInt()).isEqualTo(200);
        assertThat(json.get("finalUri").asText()).isEqualTo("https://www.shop.test/fiche/1");
        assertThat(json.get("listingId").asText()).isEqualTo("ldlc/rtx 4070");
    }

    @Test
    void purgesHtmlAndMetadataTogether(@TempDir Path root) {
        FileRawSnapshotStore store = new FileRawSnapshotStore(root, Duration.ofDays(7));
        ListingId listing = new ListingId("l");
        store.store(listing, result(FETCHED.minus(Duration.ofDays(8))));
        store.store(listing, result(FETCHED.minus(Duration.ofDays(6))));

        assertThat(store.purgeExpired(FETCHED)).isEqualTo(2);
        assertThat(root.resolve("l").resolve("20260828T081530Z.html.gz")).doesNotExist();
        assertThat(root.resolve("l").resolve("20260828T081530Z.meta.json")).doesNotExist();
        assertThat(root.resolve("l").resolve("20260830T081530Z.html.gz")).exists();
        assertThat(new FileRawSnapshotStore(root).purgeExpired(FETCHED)).isZero();
    }

    @Test
    void safeNameKeepsOnlyFilesystemFriendlyCharacters() {
        assertThat(SnapshotRetention.safeName("ok-name_1.2")).isEqualTo("ok-name_1.2");
        assertThat(SnapshotRetention.safeName("a/b\\c:d e")).isEqualTo("a_b_c_d_e");
        assertThat(SnapshotRetention.stampOf("20260905T081530Z.html.gz")).isEqualTo(FETCHED);
        assertThat(SnapshotRetention.stampOf("README.md")).isNull();
    }
}
