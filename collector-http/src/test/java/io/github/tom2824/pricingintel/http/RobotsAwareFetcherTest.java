package io.github.tom2824.pricingintel.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tom2824.pricingintel.collector.FetchException;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.collector.PageFetcher;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RobotsAwareFetcherTest {

    private final List<URI> fetched = new ArrayList<>();
    private final Map<String, Duration> raised = new HashMap<>();

    private PageFetcher serving(String robotsBody, int robotsStatus) {
        return uri -> {
            fetched.add(uri);
            if (uri.getPath().equals("/robots.txt")) {
                return new FetchResult(uri, uri, robotsStatus, "text/plain", robotsBody, Instant.EPOCH);
            }
            return new FetchResult(uri, uri, 200, "text/html", "<html/>", Instant.EPOCH);
        };
    }

    @Test
    void readsRobotsOncePerHostAndBlocksDisallowedPaths() throws FetchException {
        String robots = "User-agent: *\nDisallow: /panier/\nCrawl-delay: 4\n";
        RobotsAwareFetcher fetcher = new RobotsAwareFetcher(serving(robots, 200), "pricing-intel", raised::put);

        fetcher.fetch(URI.create("https://shop.test/fiche/1"));
        fetcher.fetch(URI.create("https://shop.test/fiche/2"));
        assertThatThrownBy(() -> fetcher.fetch(URI.create("https://shop.test/panier/etape-1")))
                .isInstanceOf(FetchException.class)
                .hasMessageContaining("robots.txt")
                .extracting("retryable").isEqualTo(false);

        assertThat(fetched).extracting(URI::getPath).containsExactly("/robots.txt", "/fiche/1", "/fiche/2");
        assertThat(raised).containsEntry("shop.test", Duration.ofSeconds(4));
    }

    @Test
    void missingRobotsMeansEverythingIsAllowed() throws FetchException {
        RobotsAwareFetcher fetcher = new RobotsAwareFetcher(serving("", 404), "pricing-intel", null);

        FetchResult result = fetcher.fetch(URI.create("https://shop.test/anything"));

        assertThat(result.status()).isEqualTo(200);
    }

    @Test
    void unreadableRobotsMeansEverythingIsAllowed() throws FetchException {
        PageFetcher failingRobots = uri -> {
            if (uri.getPath().equals("/robots.txt")) {
                throw new FetchException("timeout", true);
            }
            return new FetchResult(uri, uri, 200, "text/html", "", Instant.EPOCH);
        };

        FetchResult result = new RobotsAwareFetcher(failingRobots, "pricing-intel", null)
                .fetch(URI.create("https://shop.test/p"));

        assertThat(result.status()).isEqualTo(200);
    }
}
