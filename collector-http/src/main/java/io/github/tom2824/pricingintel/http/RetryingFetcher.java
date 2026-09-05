package io.github.tom2824.pricingintel.http;

import io.github.tom2824.pricingintel.collector.FetchException;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.collector.PageFetcher;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retente sur erreur réseau et sur les statuts transitoires (429, 502, 503, 504), avec backoff et jitter.
 * Ne retente jamais une interdiction (robots.txt) ni un 404 : ce ne sont pas des accidents.
 */
public final class RetryingFetcher implements PageFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(RetryingFetcher.class);
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 500, 502, 503, 504);

    private final PageFetcher delegate;
    private final RetryPolicy policy;
    private final Sleeper sleeper;
    private final DoubleSupplier jitter;

    public RetryingFetcher(PageFetcher delegate, RetryPolicy policy) {
        this(delegate, policy, Sleeper.system(), defaultJitter(RandomGenerator.getDefault()));
    }

    /** @param jitter facteur multiplicatif appliqué à chaque attente, typiquement dans [0.8, 1.2] */
    public RetryingFetcher(PageFetcher delegate, RetryPolicy policy, Sleeper sleeper, DoubleSupplier jitter) {
        this.delegate = delegate;
        this.policy = policy;
        this.sleeper = sleeper;
        this.jitter = jitter;
    }

    public static DoubleSupplier defaultJitter(RandomGenerator random) {
        return () -> 0.8 + random.nextDouble() * 0.4;
    }

    public static boolean isRetryableStatus(int status) {
        return RETRYABLE_STATUSES.contains(status);
    }

    @Override
    public FetchResult fetch(URI uri) throws FetchException {
        FetchException lastException = null;
        FetchResult lastResult = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            if (attempt > 1) {
                pause(attempt, uri);
            }
            try {
                FetchResult result = delegate.fetch(uri);
                if (!isRetryableStatus(result.status())) {
                    return result;
                }
                LOG.info("Attempt {}/{} for {} returned HTTP {}", attempt, policy.maxAttempts(), uri, result.status());
                lastResult = result;
                lastException = null;
            } catch (FetchException e) {
                if (!e.isRetryable()) {
                    throw e;
                }
                LOG.info("Attempt {}/{} for {} failed: {}", attempt, policy.maxAttempts(), uri, e.getMessage());
                lastException = e;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        return lastResult;
    }

    private void pause(int attempt, URI uri) throws FetchException {
        Duration base = policy.backoffBefore(attempt);
        Duration wait = Duration.ofMillis((long) (base.toMillis() * jitter.getAsDouble()));
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Interrupted while waiting to retry " + uri, e, false);
        }
    }
}
