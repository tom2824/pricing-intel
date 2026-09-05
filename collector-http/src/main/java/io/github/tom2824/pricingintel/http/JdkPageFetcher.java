package io.github.tom2824.pricingintel.http;

import io.github.tom2824.pricingintel.collector.FetchException;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.collector.PageFetcher;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetcher brut sur {@link HttpClient} du JDK. Pas de retry ni de politesse ici : c'est le rôle des décorateurs.
 * Le User-Agent identifie l'outil et pointe vers le projet, pour qu'un administrateur sache qui le contacte.
 */
public final class JdkPageFetcher implements PageFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(JdkPageFetcher.class);

    private final HttpClient client;
    private final String userAgent;
    private final Duration timeout;
    private final Clock clock;

    public JdkPageFetcher(String userAgent, Duration timeout, ProxyPolicy proxy, Clock clock) {
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(proxy.selector())
                .build();
    }

    @Override
    public FetchResult fetch(URI uri) throws FetchException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.7")
                .GET()
                .build();
        LOG.debug("GET {}", uri);
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new FetchResult(uri, response.uri(), response.statusCode(),
                    response.headers().firstValue("content-type").orElse(""), response.body(), clock.instant());
        } catch (IOException e) {
            throw new FetchException("I/O error fetching " + uri + ": " + e.getMessage(), e, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Interrupted while fetching " + uri, e, false);
        } catch (IllegalArgumentException e) {
            throw new FetchException("Invalid request for " + uri + ": " + e.getMessage(), e, false);
        }
    }
}
