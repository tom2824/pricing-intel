package io.github.tom2824.pricingintel.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tom2824.pricingintel.domain.Availability;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmbeddedJsonExtractorTest {

    @Test
    void readsNextDataScriptWithJsonPointers() {
        EmbeddedJsonExtractor extractor = new EmbeddedJsonExtractor("script#__NEXT_DATA__", null, Map.of(
                "price", "/props/pageProps/product/price",
                "listPrice", "/props/pageProps/product/oldPrice",
                "currency", "/props/pageProps/product/currency",
                "availability", "/props/pageProps/product/stock/quantity",
                "gtin", "/props/pageProps/product/ean",
                "brand", "/props/pageProps/product/brand/name",
                "title", "/props/pageProps/product/title"), "EUR");

        assertThat(extractor.extract(Fixtures.page("embedded-next.html"))).hasValueSatisfying(o -> {
            assertThat(o.price()).isEqualByComparingTo(new BigDecimal("114.9"));
            assertThat(o.listPrice()).isEqualByComparingTo(new BigDecimal("129.90"));
            assertThat(o.availability()).isEqualTo(Availability.IN_STOCK);
            assertThat(o.identity().gtin()).isEqualTo("0740617325010");
            assertThat(o.identity().brand()).isEqualTo("Kingston");
            assertThat(o.identity().title()).contains("FURY Beast");
            assertThat(o.method()).isEqualTo("embedded-json");
        });
    }

    @Test
    void readsVariableAssignmentEvenWithBracesInsideStrings() {
        EmbeddedJsonExtractor extractor = new EmbeddedJsonExtractor(null, "window.__INITIAL_STATE__", Map.of(
                "price", "/product/offer/amount",
                "availability", "/product/offer/available",
                "title", "/product/name"), "EUR");

        assertThat(extractor.extract(Fixtures.page("embedded-var.html"))).hasValueSatisfying(o -> {
            assertThat(o.price()).isEqualByComparingTo(new BigDecimal("89.90"));
            assertThat(o.availability()).isEqualTo(Availability.OUT_OF_STOCK);
            assertThat(o.identity().title()).isEqualTo("Boîtier { avec accolade } \"test\"");
        });
    }

    @Test
    void returnsEmptyWhenTheScriptOrThePriceIsMissing() {
        EmbeddedJsonExtractor extractor = new EmbeddedJsonExtractor("script#__NEXT_DATA__", null,
                Map.of("price", "/nope"), "EUR");

        assertThat(extractor.extract(Fixtures.page("embedded-next.html"))).isEmpty();
        assertThat(extractor.extract(Fixtures.page("css-product.html"))).isEmpty();
    }

    @Test
    void validatesItsConfiguration() {
        assertThatThrownBy(() -> new EmbeddedJsonExtractor(null, null, Map.of("price", "/p"), "EUR"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddedJsonExtractor("script", null, Map.of("title", "/t"), "EUR"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("price");
        assertThatThrownBy(() -> new EmbeddedJsonExtractor("script", null, Map.of("price", "props.price"), "EUR"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pointer");
    }

    @Test
    void balancedJsonStopsAtTheMatchingBracket() {
        assertThat(EmbeddedJsonExtractor.balancedJson("x = {\"a\":[1,{\"b\":\"}\"}]}; y = 2", 3))
                .isEqualTo("{\"a\":[1,{\"b\":\"}\"}]}");
        assertThat(EmbeddedJsonExtractor.balancedJson("x = 42;", 3)).isNull();
        assertThat(EmbeddedJsonExtractor.balancedJson("x = {\"unterminated\": 1", 3)).isNull();
    }
}
