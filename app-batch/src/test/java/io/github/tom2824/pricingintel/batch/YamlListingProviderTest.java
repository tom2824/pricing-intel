package io.github.tom2824.pricingintel.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tom2824.pricingintel.domain.Listing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlListingProviderTest {

    @Test
    void readsListingsWithOptionalExternalRef() {
        List<Listing> listings = new YamlListingProvider(Path.of("src/test/resources/listings-test.yml")).listings();

        assertThat(listings).hasSize(2);
        assertThat(listings.get(0).id().value()).isEqualTo("nowhere-1");
        assertThat(listings.get(0).host()).isEqualTo("nowhere.invalid");
        assertThat(listings.get(0).externalReference()).isEmpty();
        assertThat(listings.get(1).externalReference()).contains("ext-2");
    }

    @Test
    void explainsMissingFile() {
        assertThatThrownBy(() -> new YamlListingProvider(Path.of("nope/listings.yml")).listings())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listings.example.yml");
    }

    @Test
    void rejectsDuplicateIdsAndInvalidEntries(@TempDir Path dir) throws IOException {
        Path duplicates = dir.resolve("dup.yml");
        Files.writeString(duplicates, """
                listings:
                  - {id: a, product: p, source: s, url: https://x.test/1}
                  - {id: a, product: p, source: s, url: https://x.test/2}
                """);
        assertThatThrownBy(() -> new YamlListingProvider(duplicates).listings())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate");

        Path invalid = dir.resolve("invalid.yml");
        Files.writeString(invalid, "listings:\n  - {id: b, source: s, url: https://x.test/1}\n");
        assertThatThrownBy(() -> new YamlListingProvider(invalid).listings())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("'b'");
    }
}
