package io.github.tom2824.pricingintel.sink.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.ListingId;
import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.ObservedIdentity;
import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonLinesPriceSinkTest {

    private static PriceSnapshot snapshot(String id, String price) {
        return PriceSnapshot.builder(new ListingId(id), Instant.parse("2026-09-05T08:00:00Z"),
                        URI.create("https://shop.test/p/" + id), Money.eur(price))
                .listPrice(Money.eur("699.99"))
                .availability(Availability.IN_STOCK)
                .identity(new ObservedIdentity("4711377114363", "MSI", null, null, "RTX 4070 SUPER"))
                .extraction("jsonld", 0.95)
                .build();
    }

    @Test
    void appendsOneJsonObjectPerLineAndCreatesParentDirectories(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("out/releves.jsonl");

        try (JsonLinesPriceSink sink = new JsonLinesPriceSink(file)) {
            sink.accept(snapshot("a", "629.95"));
            sink.accept(snapshot("b", "12.5"));
        }
        try (JsonLinesPriceSink again = new JsonLinesPriceSink(file)) {
            again.accept(snapshot("c", "1"));
        }

        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(3);

        JsonNode first = new ObjectMapper().readTree(lines.get(0));
        assertThat(first.get("listingId").get("value").asText()).isEqualTo("a");
        assertThat(first.get("observedAt").asText()).isEqualTo("2026-09-05T08:00:00Z");
        assertThat(first.get("price").get("amount").decimalValue()).isEqualByComparingTo("629.95");
        assertThat(first.get("price").get("currency").asText()).isEqualTo("EUR");
        assertThat(first.get("availability").asText()).isEqualTo("IN_STOCK");
        assertThat(first.get("identity").get("gtin").asText()).isEqualTo("4711377114363");
        assertThat(first.get("identity").has("mpn")).isFalse();
        assertThat(first.has("shippingCost")).isFalse();
        assertThat(first.get("extraction").get("method").asText()).isEqualTo("jsonld");
        // Les méthodes dérivées des records (isDiscounted, isPositive, isEmpty) ne sont pas des données.
        assertThat(first.has("discounted")).isFalse();
        assertThat(first.get("price").has("positive")).isFalse();
        assertThat(first.get("identity").has("empty")).isFalse();
    }

    @Test
    void closingWithoutWritingDoesNotCreateTheFile(@TempDir Path dir) {
        Path file = dir.resolve("empty.jsonl");

        new JsonLinesPriceSink(file).close();

        assertThat(file).doesNotExist();
    }
}
