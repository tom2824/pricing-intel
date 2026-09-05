package io.github.tom2824.pricingintel.http;

import java.time.Duration;
import java.util.Objects;

/**
 * Tout ce qui paramètre la chaîne HTTP. Les valeurs par défaut sont volontairement prudentes :
 * une requête toutes les trois secondes par hôte, trois tentatives, robots.txt respecté, pas de proxy.
 *
 * @param userAgent chaîne complète envoyée ; son premier mot (avant {@code /}) sert de token pour robots.txt
 */
public record FetcherConfig(
        String userAgent,
        Duration timeout,
        ProxyPolicy proxy,
        Duration minIntervalPerHost,
        RetryPolicy retry,
        boolean respectRobotsTxt) {

    public static final String DEFAULT_USER_AGENT = "pricing-intel/0.1 (+https://github.com/tom2824/pricing-intel)";

    public FetcherConfig {
        Objects.requireNonNull(userAgent, "userAgent");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(minIntervalPerHost, "minIntervalPerHost");
        Objects.requireNonNull(retry, "retry");
        if (userAgent.isBlank()) {
            throw new IllegalArgumentException("userAgent is required: sites must be able to identify us");
        }
    }

    public static FetcherConfig defaults() {
        return new FetcherConfig(DEFAULT_USER_AGENT, Duration.ofSeconds(20), ProxyPolicy.none(),
                Duration.ofSeconds(3), RetryPolicy.defaults(), true);
    }

    public FetcherConfig withProxy(ProxyPolicy value) {
        return new FetcherConfig(userAgent, timeout, value, minIntervalPerHost, retry, respectRobotsTxt);
    }

    public FetcherConfig withUserAgent(String value) {
        return new FetcherConfig(value, timeout, proxy, minIntervalPerHost, retry, respectRobotsTxt);
    }

    public FetcherConfig withMinIntervalPerHost(Duration value) {
        return new FetcherConfig(userAgent, timeout, proxy, value, retry, respectRobotsTxt);
    }

    public FetcherConfig withRetry(RetryPolicy value) {
        return new FetcherConfig(userAgent, timeout, proxy, minIntervalPerHost, value, respectRobotsTxt);
    }

    public FetcherConfig withRespectRobotsTxt(boolean value) {
        return new FetcherConfig(userAgent, timeout, proxy, minIntervalPerHost, retry, value);
    }

    /** Le nom de produit du User-Agent, ex. {@code pricing-intel} pour {@code pricing-intel/0.1 (+url)}. */
    public String userAgentToken() {
        String token = userAgent.strip();
        int end = token.indexOf('/');
        if (end < 0) {
            end = token.indexOf(' ');
        }
        return end < 0 ? token : token.substring(0, end);
    }
}
