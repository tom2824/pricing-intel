package io.github.tom2824.pricingintel.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tom2824.pricingintel.collector.FetchException;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.collector.PageFetcher;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimitingFetcherTest {

    private final AtomicLong nanos = new AtomicLong();
    private final List<Duration> sleeps = new ArrayList<>();
    private final PageFetcher delegate = uri -> new FetchResult(uri, uri, 200, "", "", Instant.EPOCH);

    private RateLimitingFetcher fetcher(Duration minInterval) {
        return new RateLimitingFetcher(delegate, minInterval, sleeps::add, nanos::get);
    }

    @Test
    void firstRequestToAHostIsImmediate() throws FetchException {
        fetcher(Duration.ofSeconds(3)).fetch(URI.create("https://a.test/1"));

        assertThat(sleeps).isEmpty();
    }

    @Test
    void waitsForTheRemainingIntervalOnTheSameHost() throws FetchException {
        RateLimitingFetcher fetcher = fetcher(Duration.ofSeconds(3));

        fetcher.fetch(URI.create("https://a.test/1"));
        nanos.set(Duration.ofSeconds(1).toNanos());
        fetcher.fetch(URI.create("https://A.test/2"));

        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void doesNotWaitWhenEnoughTimeHasPassed() throws FetchException {
        RateLimitingFetcher fetcher = fetcher(Duration.ofSeconds(3));

        fetcher.fetch(URI.create("https://a.test/1"));
        nanos.set(Duration.ofSeconds(10).toNanos());
        fetcher.fetch(URI.create("https://a.test/2"));

        assertThat(sleeps).isEmpty();
    }

    @Test
    void differentHostsDoNotBlockEachOther() throws FetchException {
        RateLimitingFetcher fetcher = fetcher(Duration.ofSeconds(3));

        fetcher.fetch(URI.create("https://a.test/1"));
        fetcher.fetch(URI.create("https://b.test/1"));

        assertThat(sleeps).isEmpty();
    }

    @Test
    void crawlDelayCanOnlyRaiseTheInterval() throws FetchException {
        RateLimitingFetcher fetcher = fetcher(Duration.ofSeconds(3));

        fetcher.raiseMinInterval("a.test", Duration.ofSeconds(1));
        assertThat(fetcher.minIntervalFor("a.test")).isEqualTo(Duration.ofSeconds(3));

        fetcher.raiseMinInterval("a.test", Duration.ofSeconds(10));
        assertThat(fetcher.minIntervalFor("a.test")).isEqualTo(Duration.ofSeconds(10));

        fetcher.fetch(URI.create("https://a.test/1"));
        nanos.set(Duration.ofSeconds(4).toNanos());
        fetcher.fetch(URI.create("https://a.test/2"));
        assertThat(sleeps).containsExactly(Duration.ofSeconds(6));
    }
}
