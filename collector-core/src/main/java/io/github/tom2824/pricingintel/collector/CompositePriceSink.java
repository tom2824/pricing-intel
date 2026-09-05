package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import java.util.List;

/** Envoie chaque relevé à plusieurs sinks, dans l'ordre. Permet "fichier + base" sans que les sinks se connaissent. */
public final class CompositePriceSink implements PriceSink {

    private final List<PriceSink> sinks;

    public CompositePriceSink(List<PriceSink> sinks) {
        if (sinks.isEmpty()) {
            throw new IllegalArgumentException("At least one sink is required");
        }
        this.sinks = List.copyOf(sinks);
    }

    public static PriceSink of(List<PriceSink> sinks) {
        return sinks.size() == 1 ? sinks.get(0) : new CompositePriceSink(sinks);
    }

    @Override
    public void accept(PriceSnapshot snapshot) {
        for (PriceSink sink : sinks) {
            sink.accept(snapshot);
        }
    }

    @Override
    public void close() {
        RuntimeException first = null;
        for (PriceSink sink : sinks) {
            try {
                sink.close();
            } catch (Exception e) {
                if (first == null) {
                    first = new IllegalStateException("Failed to close sink " + sink, e);
                } else {
                    first.addSuppressed(e);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
