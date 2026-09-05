package io.github.tom2824.pricingintel.http;

import io.github.tom2824.pricingintel.collector.FetchException;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.collector.PageFetcher;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refuse de récupérer une URL que le robots.txt de l'hôte interdit à notre User-Agent. Le fichier est lu une
 * fois par hôte et par collecte, via la même chaîne (donc rate-limitée et retentée). Un robots.txt absent
 * ou illisible vaut autorisation, comme pour les moteurs de recherche. Un Crawl-delay déclaré est transmis
 * au rate limiter.
 */
public final class RobotsAwareFetcher implements PageFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(RobotsAwareFetcher.class);

    private final PageFetcher delegate;
    private final String userAgentToken;
    private final HostDelayRegistry delayRegistry;
    private final Map<String, RobotsTxt> cache = new ConcurrentHashMap<>();

    /** @param delayRegistry où propager le Crawl-delay, ou null pour l'ignorer */
    public RobotsAwareFetcher(PageFetcher delegate, String userAgentToken, HostDelayRegistry delayRegistry) {
        this.delegate = delegate;
        this.userAgentToken = userAgentToken;
        this.delayRegistry = delayRegistry;
    }

    @Override
    public FetchResult fetch(URI uri) throws FetchException {
        String host = uri.getHost();
        if (host == null) {
            throw new FetchException("URI without host: " + uri, false);
        }
        RobotsTxt robots = cache.computeIfAbsent(hostKey(uri), key -> load(uri));
        String path = uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
        if (!robots.isAllowed(path)) {
            throw new FetchException("Blocked by robots.txt of " + host + ": " + path, false);
        }
        return delegate.fetch(uri);
    }

    private RobotsTxt load(URI pageUri) {
        URI robotsUri;
        try {
            robotsUri = new URI(pageUri.getScheme(), pageUri.getRawAuthority(), "/robots.txt", null, null);
        } catch (URISyntaxException e) {
            LOG.warn("Cannot build robots.txt URI for {}: {}", pageUri, e.getMessage());
            return RobotsTxt.allowAll();
        }
        try {
            FetchResult result = delegate.fetch(robotsUri);
            if (!result.isSuccess()) {
                LOG.info("robots.txt of {} returned HTTP {}: treating as allow-all", pageUri.getHost(), result.status());
                return RobotsTxt.allowAll();
            }
            RobotsTxt robots = RobotsTxt.parse(result.body(), userAgentToken);
            if (delayRegistry != null) {
                robots.crawlDelay().ifPresent(delay -> {
                    LOG.info("robots.txt of {} asks for a crawl delay of {}", pageUri.getHost(), delay);
                    delayRegistry.raiseMinInterval(pageUri.getHost(), delay);
                });
            }
            return robots;
        } catch (FetchException e) {
            LOG.warn("Could not read robots.txt of {} ({}): treating as allow-all", pageUri.getHost(), e.getMessage());
            return RobotsTxt.allowAll();
        }
    }

    private static String hostKey(URI uri) {
        return uri.getScheme() + "://" + uri.getRawAuthority().toLowerCase();
    }
}
