package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import java.io.PrintStream;

/** Sink de démonstration et de débogage : une ligne lisible par relevé sur la sortie standard. */
public final class ConsolePriceSink implements PriceSink {

    private final PrintStream out;

    public ConsolePriceSink() {
        this(System.out);
    }

    public ConsolePriceSink(PrintStream out) {
        this.out = out;
    }

    @Override
    public void accept(PriceSnapshot s) {
        String discount = s.isDiscounted() ? " (barré " + s.listPrice() + ")" : "";
        out.printf("%s  %-28s %12s%s  %-12s %s [%s %.2f]%n",
                s.observedAt(), s.listingId(), s.price(), discount, s.availability(),
                s.identity().title() == null ? "" : s.identity().title(),
                s.extraction().method(), s.extraction().confidence());
    }
}
