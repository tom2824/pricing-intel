package io.github.tom2824.pricingintel.scraper;

import io.github.tom2824.pricingintel.collector.FetchException;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.collector.ObservationException;
import io.github.tom2824.pricingintel.collector.PageFetcher;
import io.github.tom2824.pricingintel.collector.PriceSource;
import io.github.tom2824.pricingintel.collector.RawSnapshotStore;
import io.github.tom2824.pricingintel.domain.Listing;
import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Source de prix par scraping : télécharge la page via le {@link PageFetcher}, archive la réponse brute,
 * puis fait passer la page dans la chaîne d'extracteurs du site. Le premier extracteur qui trouve un prix gagne.
 */
public final class ScraperPriceSource implements PriceSource {

    public static final String ID = "scraper";

    private static final Logger LOG = LoggerFactory.getLogger(ScraperPriceSource.class);
    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");

    private final SiteRegistry registry;
    private final PageFetcher fetcher;
    private final RawSnapshotStore rawStore;
    private final String defaultCurrency;
    private final Map<String, List<Extractor>> extractorsBySite = new ConcurrentHashMap<>();

    public ScraperPriceSource(SiteRegistry registry, PageFetcher fetcher, RawSnapshotStore rawStore, String defaultCurrency) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.rawStore = Objects.requireNonNull(rawStore, "rawStore");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(Listing listing) {
        String scheme = listing.url().getScheme();
        return scheme != null && SUPPORTED_SCHEMES.contains(scheme.toLowerCase())
                && registry.find(listing.host()).isPresent();
    }

    @Override
    public PriceSnapshot observe(Listing listing) throws ObservationException {
        SiteDefinition site = registry.find(listing.host())
                .orElseThrow(() -> new ObservationException("No site definition for host " + listing.host(), false));

        FetchResult result;
        try {
            result = fetcher.fetch(listing.url());
        } catch (FetchException e) {
            throw new ObservationException("Fetch failed for " + listing.url() + ": " + e.getMessage(), e, e.isRetryable());
        }
        archive(listing, result);
        if (!result.isSuccess()) {
            throw new ObservationException("HTTP " + result.status() + " for " + listing.url(),
                    result.isRateLimited() || result.isServerError());
        }

        Document document = Jsoup.parse(result.body(), result.finalUri().toString());
        for (Extractor extractor : extractorsFor(site)) {
            Optional<ExtractedOffer> offer;
            try {
                offer = extractor.extract(document);
            } catch (RuntimeException e) {
                LOG.warn("Extractor {} crashed on {} ({}): {}", extractor.method(), listing.url(), site.id(), e.toString());
                continue;
            }
            if (offer.isPresent()) {
                LOG.debug("{} extracted by {} on {}", listing.id(), extractor.method(), listing.url());
                return toSnapshot(listing, result, offer.get());
            }
        }
        throw new ObservationException("No extractor of site '" + site.id() + "' found a price on " + listing.url(), false);
    }

    private List<Extractor> extractorsFor(SiteDefinition site) {
        return extractorsBySite.computeIfAbsent(site.id(),
                id -> site.extractors().stream().map(spec -> spec.build(defaultCurrency)).toList());
    }

    private void archive(Listing listing, FetchResult result) {
        try {
            rawStore.store(listing.id(), result);
        } catch (RuntimeException e) {
            LOG.warn("Could not archive raw snapshot of {}: {}", listing.id(), e.toString());
        }
    }

    private static PriceSnapshot toSnapshot(Listing listing, FetchResult result, ExtractedOffer offer) throws ObservationException {
        try {
            Currency currency = Currency.getInstance(offer.currency());
            PriceSnapshot.Builder builder = PriceSnapshot
                    .builder(listing.id(), result.fetchedAt(), result.finalUri(), Money.of(offer.price(), currency))
                    .availability(offer.availability())
                    .condition(offer.condition())
                    .sellerType(offer.sellerType())
                    .identity(offer.identity())
                    .extraction(offer.method(), offer.confidence());
            if (offer.listPrice() != null) {
                builder.listPrice(Money.of(offer.listPrice(), currency));
            }
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new ObservationException("Extracted offer is invalid for " + listing.url() + ": " + e.getMessage(), e, false);
        }
    }
}
