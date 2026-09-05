package io.github.tom2824.pricingintel.batch;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tom2824.pricingintel.collector.CollectionReport;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Le profil postgres de bout en bout, sur un PostgreSQL embarqué : import du catalogue, annonces lues en base,
 * collecte (aucun site ne couvre ces hôtes dans le profil de test, donc aucune requête réseau), échecs stockés.
 */
@SpringBootTest(properties = {
        "collector.catalogue.import-file=../persistence/src/test/resources/catalogue-test.yml",
        "collector.sinks.types=console,postgres"
})
@ActiveProfiles({"test", "postgres"})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
class PostgresProfileTest {

    @Autowired
    CollectRunner runner;

    @Autowired
    JdbcClient jdbc;

    @Test
    void importsThenCollectsFromDatabaseListingsAndStoresFailures() {
        assertThat(jdbc.sql("select count(*) from product").query(Long.class).single()).isEqualTo(4L);
        assertThat(jdbc.sql("select count(*) from listing").query(Long.class).single()).isEqualTo(4L);

        assertThat(runner.lastReport()).hasValueSatisfying(report -> {
            assertThat(report.attempted()).isEqualTo(4);
            assertThat(report.collected()).isZero();
            assertThat(report.failures()).extracting(CollectionReport.Failure::sourceId).containsOnly("none");
        });
        assertThat(jdbc.sql("select count(*) from collection_run").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("select count(*) from collection_failure where listing_id is not null").query(Long.class).single())
                .isEqualTo(4L);
    }
}
