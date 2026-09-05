package io.github.tom2824.pricingintel.scraper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PriceParserTest {

    @ParameterizedTest
    @CsvSource({
            "'1 299,99 €', 1299.99",
            "'1 299,99 €', 1299.99",
            "'1299.99', 1299.99",
            "'€1,299.99', 1299.99",
            "'1299€99', 1299.99",
            "'1 299 € 00', 1299.00",
            "'1.299,00', 1299.00",
            "'1,299', 1299",
            "'1.299', 1299",
            "'129,-', 129",
            "'12,5', 12.5",
            "'Prix : 49,90 € TTC', 49.90",
            "'649.99', 649.99",
            "'5', 5",
    })
    void parsesDisplayedPrices(String raw, String expected) {
        assertThat(PriceParser.parse(raw)).contains(new BigDecimal(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Prix sur demande", "N/A"})
    void returnsEmptyWhenThereIsNoNumber(String raw) {
        assertThat(PriceParser.parse(raw)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void nullIsEmpty() {
        assertThat(PriceParser.parse(null)).isEmpty();
    }
}
