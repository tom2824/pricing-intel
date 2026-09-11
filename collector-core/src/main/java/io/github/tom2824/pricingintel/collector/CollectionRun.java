package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.Listing;
import io.github.tom2824.pricingintel.domain.ListingId;
import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Une collecte : pour chaque annonce, trouver la première source qui la prend en charge, l'observer,
 * envoyer le relevé au sink. Un échec d'observation est consigné et la collecte continue ; un échec du sink
 * est consigné lui aussi (transitoire : la prochaine exécution retentera), et la collecte s'arrête seulement
 * quand le sink échoue plusieurs fois de suite, signe qu'il est hors service : aller chercher des pages pour
 * ne rien en garder serait du trafic inutile. Dans tous les cas un bilan est produit, jamais une exception :
 * c'est le bilan qui explique un trou dans une courbe (ADR 0017).
 *
 * <p>Une annonce déjà relevée aujourd'hui n'est pas refetchée (un relevé par annonce et par jour, ADR 0017) :
 * une seconde exécution le même jour ne rattrape que les échecs de la première.
 */
public final class CollectionRun {

    /** Identifiant de source des échecs imputables au sink, pas à l'enseigne. */
    public static final String SINK = "sink";

    /** Au-delà, le sink est considéré hors service et la collecte s'arrête. */
    static final int MAX_CONSECUTIVE_SINK_FAILURES = 3;

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
        Set<ListingId> alreadyCollected = listingProvider.collectedOn(LocalDate.ofInstant(startedAt, ZoneOffset.UTC));
        List<CollectionReport.Failure> failures = new ArrayList<>();
        int collected = 0;
        int skipped = 0;
        int consecutiveSinkFailures = 0;

        for (int i = 0; i < listings.size(); i++) {
            Listing listing = listings.get(i);
            if (alreadyCollected.contains(listing.id())) {
                skipped++;
                collected++;
                continue;
            }
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
            try {
                sink.accept(snapshot);
                consecutiveSinkFailures = 0;
                collected++;
            } catch (RuntimeException e) {
                failures.add(new CollectionReport.Failure(listing.id(), SINK, "Sink error: " + e, true));
                if (++consecutiveSinkFailures >= MAX_CONSECUTIVE_SINK_FAILURES) {
                    int remaining = listings.size() - i - 1;
                    failures.add(new CollectionReport.Failure(listing.id(), SINK,
                            "Collecte interrompue : " + consecutiveSinkFailures + " échecs consécutifs du sink, "
                                    + remaining + " annonce(s) non tentée(s)", true));
                    break;
                }
            }
        }

        return new CollectionReport(startedAt, clock.instant(), listings.size(), collected, skipped, failures);
    }
}
