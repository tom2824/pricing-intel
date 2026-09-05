package io.github.tom2824.pricingintel.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tom2824.pricingintel.domain.Availability;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CssExtractorTest {

    @Test
    void readsTextSelectorsAndAvailabilityKeywords() {
        ExtractorSpec.Css spec = new ExtractorSpec.Css(".pricing .price", ".pricing .price-old", ".stock",
                null, null, null, ".brand", null, null, "h1.product-title", null);

        assertThat(spec.build("EUR").extract(Fixtures.page("css-product.html"))).hasValueSatisfying(o -> {
            assertThat(o.price()).isEqualByComparingTo(new BigDecimal("1299.00"));
            assertThat(o.listPrice()).isEqualByComparingTo(new BigDecimal("1349.00"));
            assertThat(o.availability()).isEqualTo(Availability.IN_STOCK);
            assertThat(o.currency()).isEqualTo("EUR");
            assertThat(o.identity().brand()).isEqualTo("AMD");
            assertThat(o.identity().title()).isEqualTo("AMD Ryzen 7 9800X3D");
            assertThat(o.method()).isEqualTo("css");
            assertThat(o.confidence()).isEqualTo(0.7);
        });
    }

    @Test
    void readsAttributesWithTheAtSyntax() {
        ExtractorSpec.Css spec = ExtractorSpec.Css.price("meta[itemprop=price] @content");

        assertThat(spec.build("EUR").extract(Fixtures.page("css-product.html")))
                .map(ExtractedOffer::price)
                .hasValueSatisfying(price -> assertThat(price).isEqualByComparingTo(new BigDecimal("1299.00")));
    }

    @Test
    void returnsEmptyWhenThePriceSelectorMatchesNothing() {
        assertThat(ExtractorSpec.Css.price(".missing").build("EUR").extract(Fixtures.page("css-product.html"))).isEmpty();
    }

    @Test
    void outOfStockKeywordsWinOverInStockOnes() {
        assertThat(CssExtractor.availabilityFromText("Indisponible", null, null)).isEqualTo(Availability.OUT_OF_STOCK);
        assertThat(CssExtractor.availabilityFromText("Disponible", null, null)).isEqualTo(Availability.IN_STOCK);
        assertThat(CssExtractor.availabilityFromText("En précommande", null, null)).isEqualTo(Availability.PREORDER);
        assertThat(CssExtractor.availabilityFromText("Livraison offerte", null, null)).isEqualTo(Availability.UNKNOWN);
        assertThat(CssExtractor.availabilityFromText("Dispo", List.of("dispo"), List.of())).isEqualTo(Availability.IN_STOCK);
    }

    @Test
    void requiresAPriceSelector() {
        assertThatThrownBy(() -> ExtractorSpec.Css.price(" ").build("EUR"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
