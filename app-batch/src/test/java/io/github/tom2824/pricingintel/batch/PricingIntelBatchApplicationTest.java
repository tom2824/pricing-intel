package io.github.tom2824.pricingintel.batch;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tom2824.pricingintel.collector.CollectionReport;
import io.github.tom2824.pricingintel.http.FetcherConfig;
import io.github.tom2824.pricingintel.http.ProxyPolicy;
import io.github.tom2824.pricingintel.scraper.SiteRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Le contexte démarre et la collecte tourne sur des annonces dont l'hôte n'est couvert par aucun site :
 * aucune requête réseau n'est faite, et le bilan doit le dire clairement.
 */
@SpringBootTest(properties = {
        "collector.proxy.mode=fixed",
        "collector.proxy.host=proxy.test",
        "collector.proxy.port=3128",
        "collector.min-interval-per-host=7s",
        "collector.retry.max-attempts=5"
})
@ActiveProfiles("test")
class PricingIntelBatchApplicationTest {

    @Autowired
    CollectRunner runner;

    @Autowired
    FetcherConfig fetcherConfig;

    @Autowired
    SiteRegistry siteRegistry;

    @Test
    void runsACollectionAtStartupAndReportsUnsupportedListings() {
        assertThat(runner.lastReport()).hasValueSatisfying(report -> {
            assertThat(report.attempted()).isEqualTo(2);
            assertThat(report.collected()).isZero();
            assertThat(report.failures()).extracting(CollectionReport.Failure::sourceId).containsOnly("none");
        });
        assertThat(runner.getExitCode()).isEqualTo(1);
    }

    @Test
    void bindsPropertiesIntoTheHttpConfiguration() {
        assertThat(fetcherConfig.proxy()).isEqualTo(ProxyPolicy.fixed("proxy.test", 3128));
        assertThat(fetcherConfig.minIntervalPerHost()).isEqualTo(Duration.ofSeconds(7));
        assertThat(fetcherConfig.retry().maxAttempts()).isEqualTo(5);
        assertThat(fetcherConfig.userAgentToken()).isEqualTo("pricing-intel");
    }

    @Test
    void loadsSiteDefinitionsFromTheConfiguredDirectory() {
        assertThat(siteRegistry.find("www.shop.test")).isPresent();
        assertThat(siteRegistry.find("nowhere.invalid")).isEmpty();
    }
}
