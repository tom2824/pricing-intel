package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.ListingId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Bilan d'une collecte : combien d'annonces tentées, combien ont leur relevé du jour, et chaque échec expliqué.
 *
 * @param attempted annonces à relever
 * @param collected annonces qui ont un relevé aujourd'hui, {@code skipped} comprises
 * @param skipped   annonces déjà relevées avant cette exécution, donc pas refetchées
 */
public record CollectionReport(Instant startedAt, Instant finishedAt, int attempted, int collected, int skipped,
                               List<Failure> failures) {

    public CollectionReport {
        failures = List.copyOf(failures);
    }

    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    /** Vrai si rien n'a été collecté alors qu'il y avait quelque chose à collecter. */
    public boolean isTotalFailure() {
        return attempted > 0 && collected == 0;
    }

    public String summary() {
        return "%d/%d relevés collectés%s, %d échec(s), %d ms".formatted(
                collected, attempted, skipped > 0 ? " (dont " + skipped + " déjà relevés aujourd'hui)" : "",
                failures.size(), duration().toMillis());
    }

    /**
     * @param sourceId  source qui a échoué, {@code none} si aucune source ne prend l'annonce en charge,
     *                  {@link CollectionRun#SINK} si le relevé a été obtenu mais pas conservé
     * @param retryable vrai si retenter plus tard a un sens
     */
    public record Failure(ListingId listingId, String sourceId, String reason, boolean retryable) {
    }
}
