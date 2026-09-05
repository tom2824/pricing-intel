package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.ListingId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Bilan d'une collecte : combien d'annonces tentées, combien de relevés produits, et chaque échec expliqué. */
public record CollectionReport(Instant startedAt, Instant finishedAt, int attempted, int collected, List<Failure> failures) {

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
        return "%d/%d relevés collectés, %d échec(s), %d ms".formatted(
                collected, attempted, failures.size(), duration().toMillis());
    }

    /**
     * @param sourceId  source qui a échoué, ou {@code none} si aucune source ne prend l'annonce en charge
     * @param retryable vrai si retenter plus tard a un sens
     */
    public record Failure(ListingId listingId, String sourceId, String reason, boolean retryable) {
    }
}
