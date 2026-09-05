package io.github.tom2824.pricingintel.http;

import io.github.tom2824.pricingintel.collector.PageFetcher;
import java.time.Clock;

/** Assemble la chaîne de décorateurs standard. Seul point d'entrée que le reste de l'application a besoin de connaître. */
public final class Fetchers {

    private Fetchers() {
    }

    /** robots.txt → retry → rate limit → JDK. */
    public static PageFetcher polite(FetcherConfig config) {
        return polite(config, Clock.systemUTC());
    }

    public static PageFetcher polite(FetcherConfig config, Clock clock) {
        PageFetcher raw = new JdkPageFetcher(config.userAgent(), config.timeout(), config.proxy(), clock);
        RateLimitingFetcher rateLimited = new RateLimitingFetcher(raw, config.minIntervalPerHost());
        PageFetcher retried = new RetryingFetcher(rateLimited, config.retry());
        return config.respectRobotsTxt()
                ? new RobotsAwareFetcher(retried, config.userAgentToken(), rateLimited)
                : retried;
    }
}
