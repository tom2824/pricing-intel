package io.github.tom2824.pricingintel.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tom2824.pricingintel.collector.FetchException;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.collector.PageFetcher;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryingFetcherTest {

    private static final URI URL = URI.create("https://shop.test/p/1");
    private static final RetryPolicy POLICY = new RetryPolicy(3, Duration.ofSeconds(2), 2.0, Duration.ofSeconds(30));

    private final List<Duration> sleeps = new ArrayList<>();
    private final Sleeper recordingSleeper = sleeps::add;

    @Test
    void returnsFirstSuccessfulResponse() throws FetchException {
        PageFetcher delegate = script(status(503), status(200));

        FetchResult result = new RetryingFetcher(delegate, POLICY, recordingSleeper, () -> 1.0).fetch(URL);

        assertThat(result.status()).isEqualTo(200);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void backsOffExponentiallyWithJitter() throws FetchException {
        PageFetcher delegate = script(status(429), status(502), status(200));

        new RetryingFetcher(delegate, POLICY, recordingSleeper, () -> 0.5).fetch(URL);

        assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @Test
    void givesUpAfterMaxAttemptsAndReturnsLastResponse() throws FetchException {
        PageFetcher delegate = script(status(503), status(503), status(503));

        FetchResult result = new RetryingFetcher(delegate, POLICY, recordingSleeper, () -> 1.0).fetch(URL);

        assertThat(result.status()).isEqualTo(503);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void doesNotRetryNonRetryableStatusesOrExceptions() throws FetchException {
        FetchResult notFound = new RetryingFetcher(script(status(404)), POLICY, recordingSleeper, () -> 1.0).fetch(URL);
        assertThat(notFound.status()).isEqualTo(404);

        PageFetcher forbidden = uri -> {
            throw new FetchException("Blocked by robots.txt", false);
        };
        assertThatThrownBy(() -> new RetryingFetcher(forbidden, POLICY, recordingSleeper, () -> 1.0).fetch(URL))
                .isInstanceOf(FetchException.class)
                .hasMessageContaining("robots.txt");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void rethrowsLastNetworkErrorAfterExhaustingAttempts() {
        PageFetcher flaky = uri -> {
            throw new FetchException("connection reset", true);
        };

        assertThatThrownBy(() -> new RetryingFetcher(flaky, POLICY, recordingSleeper, () -> 1.0).fetch(URL))
                .isInstanceOf(FetchException.class)
                .hasMessage("connection reset");
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void retryPolicyCapsBackoff() {
        RetryPolicy policy = new RetryPolicy(10, Duration.ofSeconds(2), 2.0, Duration.ofSeconds(5));

        assertThat(policy.backoffBefore(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.backoffBefore(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.backoffBefore(4)).isEqualTo(Duration.ofSeconds(5));
    }

    private static FetchResult status(int status) {
        return new FetchResult(URL, URL, status, "text/html", "", Instant.EPOCH);
    }

    private static PageFetcher script(FetchResult... responses) {
        Deque<FetchResult> queue = new ArrayDeque<>(List.of(responses));
        return uri -> {
            if (queue.isEmpty()) {
                throw new AssertionError("More fetches than scripted responses");
            }
            return queue.poll();
        };
    }
}
