package io.github.tom2824.pricingintel.persistence;

import static io.github.tom2824.pricingintel.persistence.PostgresPriceSink.QUARANTINE_CONFIRMED;
import static io.github.tom2824.pricingintel.persistence.PostgresPriceSink.QUARANTINE_NONE;
import static io.github.tom2824.pricingintel.persistence.PostgresPriceSink.QUARANTINE_REJECTED;
import static io.github.tom2824.pricingintel.persistence.PostgresPriceSink.QUARANTINE_SUSPECT;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.tom2824.pricingintel.persistence.PostgresPriceSink.Context;
import io.github.tom2824.pricingintel.persistence.PostgresPriceSink.Decision;
import io.github.tom2824.pricingintel.persistence.PostgresPriceSink.Previous;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** La décision de quarantaine (ADR 0017) sans base : chaque branche, sur des relevés de référence en mémoire. */
class PostgresPriceSinkDecisionTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("0.500");

    @Test
    void firstSnapshotOfAListingIsNeverSuspect() {
        assertThat(PostgresPriceSink.decide(new Context(1, THRESHOLD, null, null), price("1.00")))
                .isEqualTo(Decision.of(QUARANTINE_NONE));
    }

    @Test
    void priceWithinThresholdOfTheTrustedReferenceIsFine() {
        Previous last = new Previous(10, price("600.00"), QUARANTINE_NONE);

        assertThat(PostgresPriceSink.decide(new Context(1, THRESHOLD, last, last), price("615.00")))
                .isEqualTo(Decision.of(QUARANTINE_NONE));
    }

    @Test
    void priceFarFromTheTrustedReferenceBecomesSuspect() {
        Previous last = new Previous(10, price("600.00"), QUARANTINE_NONE);

        assertThat(PostgresPriceSink.decide(new Context(1, THRESHOLD, last, last), price("199.00")))
                .isEqualTo(Decision.of(QUARANTINE_SUSPECT));
    }

    @Test
    void aSuspectIsConfirmedWhenTheNextPriceAgreesWithIt() {
        Previous suspect = new Previous(11, price("199.00"), QUARANTINE_SUSPECT);
        Previous trusted = new Previous(10, price("600.00"), QUARANTINE_NONE);

        assertThat(PostgresPriceSink.decide(new Context(1, THRESHOLD, suspect, trusted), price("205.00")))
                .isEqualTo(new Decision(QUARANTINE_NONE, 11L, QUARANTINE_CONFIRMED));
    }

    @Test
    void aSuspectIsRejectedWhenTheNextPriceReturnsToTheTrustedReference() {
        Previous suspect = new Previous(11, price("64.00"), QUARANTINE_SUSPECT);
        Previous trusted = new Previous(10, price("640.00"), QUARANTINE_NONE);

        assertThat(PostgresPriceSink.decide(new Context(1, THRESHOLD, suspect, trusted), price("639.00")))
                .isEqualTo(new Decision(QUARANTINE_NONE, 11L, QUARANTINE_REJECTED));
    }

    @Test
    void aSuspectStaysOpenWhenTheNextPriceAgreesWithNeither() {
        Previous suspect = new Previous(11, price("60.00"), QUARANTINE_SUSPECT);
        Previous trusted = new Previous(10, price("600.00"), QUARANTINE_NONE);

        assertThat(PostgresPriceSink.decide(new Context(1, THRESHOLD, suspect, trusted), price("6000.00")))
                .isEqualTo(Decision.of(QUARANTINE_SUSPECT));
    }

    @Test
    void withoutAnyTrustedReferenceTheLastSnapshotIsTheReference() {
        Previous rejected = new Previous(12, price("500.00"), QUARANTINE_REJECTED);

        assertThat(PostgresPriceSink.decide(new Context(1, THRESHOLD, rejected, null), price("520.00")))
                .isEqualTo(Decision.of(QUARANTINE_NONE));
        assertThat(PostgresPriceSink.decide(new Context(1, THRESHOLD, rejected, null), price("50.00")))
                .isEqualTo(Decision.of(QUARANTINE_SUSPECT));
    }

    private static BigDecimal price(String amount) {
        return new BigDecimal(amount);
    }
}
