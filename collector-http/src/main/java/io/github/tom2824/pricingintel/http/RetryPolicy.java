package io.github.tom2824.pricingintel.http;

import java.time.Duration;

/**
 * Backoff exponentiel plafonné. Attente avant la tentative n (n ≥ 2) : {@code initial × multiplier^(n-2)},
 * bornée par {@code max}. Le décorateur y ajoute un jitter pour ne pas retenter en rafale.
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, double multiplier, Duration maxBackoff) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (initialBackoff.isNegative() || maxBackoff.isNegative()) {
            throw new IllegalArgumentException("backoff durations cannot be negative");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1");
        }
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofSeconds(2), 2.0, Duration.ofSeconds(30));
    }

    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, 1.0, Duration.ZERO);
    }

    /** @param attempt numéro de la tentative qui va suivre l'attente, à partir de 2 */
    public Duration backoffBefore(int attempt) {
        if (attempt < 2) {
            return Duration.ZERO;
        }
        double factor = Math.pow(multiplier, attempt - 2);
        long millis = (long) Math.min(initialBackoff.toMillis() * factor, (double) maxBackoff.toMillis());
        return Duration.ofMillis(millis);
    }
}
