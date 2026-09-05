package io.github.tom2824.pricingintel.persistence;

import java.time.Clock;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Assemblage de l'adaptateur PostgreSQL, actif sous le profil {@code postgres} seulement : sans base configurée,
 * l'application tourne en mode fichiers comme avant.
 */
@Configuration
@Profile("postgres")
@EnableTransactionManagement
@EnableJpaRepositories(basePackageClasses = PersistenceConfiguration.class)
@EntityScan(basePackageClasses = PersistenceConfiguration.class)
public class PersistenceConfiguration {

    @Bean
    public PostgresPriceSink postgresPriceSink(JdbcClient jdbc) {
        return new PostgresPriceSink(jdbc);
    }

    @Bean
    public PostgresListingProvider postgresListingProvider(ListingRepository listings, JdbcClient jdbc) {
        return new PostgresListingProvider(listings, jdbc);
    }

    @Bean
    public PostgresCollectionReportSink postgresCollectionReportSink(JdbcClient jdbc) {
        return new PostgresCollectionReportSink(jdbc);
    }

    @Bean
    public CatalogueImporter catalogueImporter(ProductFamilyRepository families,
                                               ProductRepository products,
                                               ProductIdentifierRepository identifiers,
                                               SourceRepository sources,
                                               ListingRepository listings,
                                               ListingMatchRepository matches,
                                               Clock clock) {
        return new CatalogueImporter(families, products, identifiers, sources, listings, matches, clock);
    }
}
