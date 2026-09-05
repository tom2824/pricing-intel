package io.github.tom2824.pricingintel.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tom2824.pricingintel.domain.ListingId;
import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositePriceSinkTest {

    private static final PriceSnapshot SNAPSHOT = PriceSnapshot
            .builder(new ListingId("l"), Instant.EPOCH, URI.create("https://x.test"), Money.eur("1"))
            .build();

    @Test
    void forwardsToEverySinkInOrder() {
        List<String> calls = new ArrayList<>();
        PriceSink first = s -> calls.add("first");
        PriceSink second = s -> calls.add("second");

        CompositePriceSink.of(List.of(first, second)).accept(SNAPSHOT);

        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void ofReturnsTheSingleSinkUnwrapped() {
        PriceSink only = s -> {
        };

        assertThat(CompositePriceSink.of(List.of(only))).isSameAs(only);
    }

    @Test
    void closesEverySinkEvenIfOneFails() {
        List<String> closed = new ArrayList<>();
        PriceSink failing = new PriceSink() {
            @Override
            public void accept(PriceSnapshot snapshot) {
            }

            @Override
            public void close() {
                throw new IllegalStateException("boom");
            }
        };
        PriceSink fine = new PriceSink() {
            @Override
            public void accept(PriceSnapshot snapshot) {
            }

            @Override
            public void close() {
                closed.add("fine");
            }
        };

        CompositePriceSink composite = new CompositePriceSink(List.of(failing, fine));

        assertThatThrownBy(composite::close).isInstanceOf(IllegalStateException.class);
        assertThat(closed).containsExactly("fine");
    }
}
