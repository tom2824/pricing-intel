package io.github.tom2824.pricingintel.http;

import io.github.tom2824.pricingintel.collector.FetchException;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.collector.PageFetcher;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Impose un intervalle minimum entre deux requêtes vers un même hôte. Les hôtes différents ne se gênent pas.
 * C'est la mesure de politesse la plus importante : à une requête toutes les quelques secondes,
 * un site ne voit même pas passer la collecte.
 */
public final class RateLimitingFetcher implements PageFetcher, HostDelayRegistry {

    private final PageFetcher delegate;
    private final Duration defaultMinInterval;
    private final Sleeper sleeper;
    private final LongSupplier nanoTime;
    private final Map<String, HostState> hosts = new ConcurrentHashMap<>();

    public RateLimitingFetcher(PageFetcher delegate, Duration defaultMinInterval) {
        this(delegate, defaultMinInterval, Sleeper.system(), System::nanoTime);
    }

    public RateLimitingFetcher(PageFetcher delegate, Duration defaultMinInterval, Sleeper sleeper, LongSupplier nanoTime) {
        if (defaultMinInterval.isNegative()) {
            throw new IllegalArgumentException("minInterval cannot be negative");
        }
        this.delegate = delegate;
        this.defaultMinInterval = defaultMinInterval;
        this.sleeper = sleeper;
        this.nanoTime = nanoTime;
    }

    @Override
    public FetchResult fetch(URI uri) throws FetchException {
        HostState state = hosts.computeIfAbsent(hostKey(uri), h -> new HostState(defaultMinInterval));
        synchronized (state) {
            if (state.hasPrevious) {
                long elapsed = nanoTime.getAsLong() - state.lastRequestNanos;
                long remaining = state.minInterval.toNanos() - elapsed;
                if (remaining > 0) {
                    pause(Duration.ofNanos(remaining), uri);
                }
            }
            try {
                return delegate.fetch(uri);
            } finally {
                state.lastRequestNanos = nanoTime.getAsLong();
                state.hasPrevious = true;
            }
        }
    }

    @Override
    public void raiseMinInterval(String host, Duration interval) {
        HostState state = hosts.computeIfAbsent(host.toLowerCase(), h -> new HostState(defaultMinInterval));
        synchronized (state) {
            if (interval.compareTo(state.minInterval) > 0) {
                state.minInterval = interval;
            }
        }
    }

    Duration minIntervalFor(String host) {
        HostState state = hosts.get(host.toLowerCase());
        return state == null ? defaultMinInterval : state.minInterval;
    }

    private void pause(Duration duration, URI uri) throws FetchException {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Interrupted while rate limiting " + uri, e, false);
        }
    }

    private static String hostKey(URI uri) {
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase();
    }

    private static final class HostState {
        private Duration minInterval;
        private long lastRequestNanos;
        private boolean hasPrevious;

        private HostState(Duration minInterval) {
            this.minInterval = minInterval;
        }
    }
}
