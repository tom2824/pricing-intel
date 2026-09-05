package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.Listing;
import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Une collecte : pour chaque annonce, trouver la première source qui la prend en charge, l'observer,
 * envoyer le relevé au sink. Un échec d'observation est consigné et la collecte continue ;
 * un échec du sink interrompt tout.
 */
public final class CollectionRun {

    private final List<PriceSource> sources;
    private final PriceSink sink;
    private final ListingProvider listingProvider;
    private final Clock clock;

    public CollectionRun(List<PriceSource> sources, PriceSink sink, ListingProvider listingProvider, Clock clock) {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("At least one PriceSource is required");
        }
        this.sources = List.copyOf(sources);
        this.sink = Objects.requireNonNull(sink, "sink");
        this.listingProvider = Objects.requireNonNull(listingProvider, "listingProvider");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CollectionReport run() {
        Instant startedAt = clock.instant();
        List<Listing> listings = listingProvider.listings();
        List<CollectionReport.Failure> failures = new ArrayList<>();
        int collected = 0;

        for (Listing listing : listings) {
            Optional<PriceSource> source = sources.stream().filter(s -> s.supports(listing)).findFirst();
            if (source.isEmpty()) {
                failures.add(new CollectionReport.Failure(listing.id(), "none",
                        "No source supports " + listing.url(), false));
                continue;
            }
            PriceSnapshot snapshot;
            try {
                snapshot = source.get().observe(listing);
            } catch (ObservationException e) {
                failures.add(new CollectionReport.Failure(listing.id(), source.get().id(), e.getMessage(), e.isRetryable()));
                continue;
            } catch (RuntimeException e) {
                failures.add(new CollectionReport.Failure(listing.id(), source.get().id(), "Unexpected error: " + e, false));
                continue;
            }
            // Volontairement hors du try : un sink qui échoue doit interrompre la collecte, pas être consigné.
            sink.accept(snapshot);
            collected++;
        }

        return new CollectionReport(startedAt, clock.instant(), listings.size(), collected, failures);
    }
}
