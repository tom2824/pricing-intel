package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.collector.CollectionRun;
import io.github.tom2824.pricingintel.collector.CompositePriceSink;
import io.github.tom2824.pricingintel.collector.CompositeRawSnapshotStore;
import io.github.tom2824.pricingintel.collector.ConsolePriceSink;
import io.github.tom2824.pricingintel.collector.ListingProvider;
import io.github.tom2824.pricingintel.collector.PageFetcher;
import io.github.tom2824.pricingintel.collector.PriceSink;
import io.github.tom2824.pricingintel.collector.PriceSource;
import io.github.tom2824.pricingintel.collector.RawSnapshotStore;
import io.github.tom2824.pricingintel.http.FetcherConfig;
import io.github.tom2824.pricingintel.http.Fetchers;
import io.github.tom2824.pricingintel.scraper.ScraperPriceSource;
import io.github.tom2824.pricingintel.scraper.SiteDefinition;
import io.github.tom2824.pricingintel.scraper.SiteDefinitionLoader;
import io.github.tom2824.pricingintel.scraper.SiteRegistry;
import io.github.tom2824.pricingintel.sink.file.DistilledSnapshotStore;
import io.github.tom2824.pricingintel.sink.file.FileRawSnapshotStore;
import io.github.tom2824.pricingintel.sink.file.JsonLinesPriceSink;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assemblage des briques à partir de la configuration. C'est ici, et seulement ici, que les adaptateurs
 * concrets (client HTTP, scraper, sinks fichier) sont branchés sur les ports du cœur.
 */
@Configuration
@EnableConfigurationProperties(CollectorProperties.class)
class CollectorConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CollectorConfiguration.class);

    /** UTC à la milliseconde : assez précis pour un relevé, et des horodatages lisibles dans les fichiers. */
    @Bean
    Clock clock() {
        return Clock.tickMillis(ZoneOffset.UTC);
    }

    @Bean
    FetcherConfig fetcherConfig(CollectorProperties properties) {
        FetcherConfig config = properties.toFetcherConfig();
        LOG.info("HTTP: user-agent '{}', {} between requests per host, {} attempt(s), robots.txt {}, proxy {}",
                config.userAgent(), config.minIntervalPerHost(), config.retry().maxAttempts(),
                config.respectRobotsTxt() ? "respected" : "IGNORED", properties.proxy().mode());
        return config;
    }

    @Bean
    PageFetcher pageFetcher(FetcherConfig config, Clock clock) {
        return Fetchers.polite(config, clock);
    }

    @Bean
    RawSnapshotStore rawSnapshotStore(CollectorProperties properties) {
        CollectorProperties.Raw raw = properties.raw();
        if (!raw.enabled()) {
            return RawSnapshotStore.none();
        }
        Path root = Path.of(raw.dir());
        List<RawSnapshotStore> stores = new ArrayList<>();
        if (raw.distilled().enabled()) {
            stores.add(new DistilledSnapshotStore(root, retentionOrNull(raw.distilled().retention())));
            LOG.info("Raw pages: distilled archive under {} (retention {})", root, describe(raw.distilled().retention()));
        }
        if (raw.html().enabled()) {
            stores.add(new FileRawSnapshotStore(root, retentionOrNull(raw.html().retention())));
            LOG.info("Raw pages: full HTML archive under {} (retention {})", root, describe(raw.html().retention()));
        }
        return CompositeRawSnapshotStore.of(stores);
    }

    private static Duration retentionOrNull(Duration retention) {
        return retention == null || retention.isZero() || retention.isNegative() ? null : retention;
    }

    private static String describe(Duration retention) {
        return retentionOrNull(retention) == null ? "unlimited" : retention.toDays() + " days";
    }

    @Bean
    SiteRegistry siteRegistry(CollectorProperties properties) {
        List<SiteDefinition> sites = new SiteDefinitionLoader().loadAll(Path.of(properties.sites().dir()));
        LOG.info("Sites: {} definition(s) loaded from {}{}", sites.size(), properties.sites().dir(),
                properties.sites().allowUnknownHosts() ? ", unknown hosts tried with JSON-LD" : ", unknown hosts refused");
        return properties.sites().allowUnknownHosts()
                ? SiteRegistry.withGenericFallback(sites)
                : SiteRegistry.strict(sites);
    }

    @Bean
    PriceSource scraperPriceSource(SiteRegistry registry, PageFetcher fetcher, RawSnapshotStore rawStore,
                                   CollectorProperties properties) {
        return new ScraperPriceSource(registry, fetcher, rawStore, properties.defaultCurrency());
    }

    @Bean
    ListingProvider listingProvider(CollectorProperties properties) {
        return new YamlListingProvider(Path.of(properties.listings().file()));
    }

    @Bean(destroyMethod = "")
    PriceSink priceSink(CollectorProperties properties) {
        List<PriceSink> sinks = properties.sinks().types().stream()
                .map(type -> switch (type) {
                    case CONSOLE -> (PriceSink) new ConsolePriceSink();
                    case JSONL -> new JsonLinesPriceSink(Path.of(properties.sinks().jsonlFile()));
                })
                .toList();
        LOG.info("Sinks: {}", properties.sinks().types());
        return CompositePriceSink.of(sinks);
    }

    @Bean
    CollectionRun collectionRun(List<PriceSource> sources, PriceSink sink, ListingProvider listingProvider, Clock clock) {
        return new CollectionRun(sources, sink, listingProvider, clock);
    }
}
