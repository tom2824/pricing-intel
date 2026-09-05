package io.github.tom2824.pricingintel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void roundsToTwoDecimalsHalfUp() {
        assertThat(Money.eur("12.345").amount()).isEqualByComparingTo("12.35");
        assertThat(Money.eur("12").amount()).isEqualByComparingTo(new BigDecimal("12.00"));
    }

    @Test
    void comparesAmountsOfSameCurrency() {
        assertThat(Money.eur("10.00").isGreaterThan(Money.eur("9.99"))).isTrue();
        assertThat(Money.eur("10.00")).isEqualByComparingTo(Money.eur("10"));
    }

    @Test
    void refusesToCompareDifferentCurrencies() {
        assertThatThrownBy(() -> Money.eur("1").compareTo(Money.of("1", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
