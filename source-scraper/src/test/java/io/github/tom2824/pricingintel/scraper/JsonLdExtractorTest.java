package io.github.tom2824.pricingintel.scraper;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.ItemCondition;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JsonLdExtractorTest {

    private final JsonLdExtractor extractor = new JsonLdExtractor("EUR");

    @Test
    void readsProductWithOffersPreferringTheInStockOne() {
        Optional<ExtractedOffer> offer = extractor.extract(Fixtures.page("jsonld-product.html"));

        assertThat(offer).hasValueSatisfying(o -> {
            assertThat(o.price()).isEqualByComparingTo(new BigDecimal("629.95"));
            assertThat(o.currency()).isEqualTo("EUR");
            assertThat(o.availability()).isEqualTo(Availability.IN_STOCK);
            assertThat(o.condition()).isEqualTo(ItemCondition.NEW);
            assertThat(o.identity().gtin()).isEqualTo("4711377114363");
            assertThat(o.identity().brand()).isEqualTo("MSI");
            assertThat(o.identity().mpn()).isEqualTo("RTX 4070 SUPER 12G VENTUS 2X OC");
            assertThat(o.identity().sku()).isEqualTo("AB12345");
            assertThat(o.identity().title()).startsWith("MSI GeForce RTX 4070 SUPER");
            assertThat(o.method()).isEqualTo("jsonld");
            assertThat(o.confidence()).isEqualTo(0.95);
        });
    }

    @Test
    void findsProductInsideGraphAndReadsAggregateOfferLowPrice() {
        Optional<ExtractedOffer> offer = extractor.extract(Fixtures.page("jsonld-graph.html"));

        assertThat(offer).hasValueSatisfying(o -> {
            assertThat(o.price()).isEqualByComparingTo(new BigDecimal("1199.00"));
            assertThat(o.identity().brand()).isEqualTo("Samsung");
            assertThat(o.identity().gtin()).isEqualTo("8806094215038");
            assertThat(o.availability()).isEqualTo(Availability.UNKNOWN);
        });
    }

    @Test
    void returnsEmptyWhenNoProductIsDeclared() {
        assertThat(extractor.extract(Fixtures.page("no-price.html"))).isEmpty();
        assertThat(extractor.extract(Fixtures.page("css-product.html"))).isEmpty();
    }
}
