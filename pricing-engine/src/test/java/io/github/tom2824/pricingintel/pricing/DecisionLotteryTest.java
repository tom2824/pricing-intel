package io.github.tom2824.pricingintel.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tom2824.pricingintel.domain.ProductId;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DecisionLotteryTest {

    private static final List<String> KEYS = List.of("index-98", "index-95", "index-100", "align", "undercut-1", "cost-plus-25");

    @Test
    void sameDayAndProductAlwaysPickTheSameRule() {
        LocalDate day = LocalDate.of(2026, 9, 9);
        String first = DecisionLottery.pick(day, new ProductId("17"), KEYS);
        for (int i = 0; i < 20; i++) {
            assertThat(DecisionLottery.pick(day, new ProductId("17"), KEYS)).isEqualTo(first);
        }
    }

    @Test
    void differentDaysSpreadOverAllRules() {
        Set<String> seen = new HashSet<>();
        for (int d = 0; d < 200; d++) {
            seen.add(DecisionLottery.pick(LocalDate.of(2026, 1, 1).plusDays(d), new ProductId("1"), KEYS));
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(KEYS);
    }

    @Test
    void refusesAnEmptyCandidateList() {
        assertThatThrownBy(() -> DecisionLottery.pick(LocalDate.of(2026, 9, 9), new ProductId("1"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
