package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.persistence.PostgresRetention;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dernière étape du batch : élague la base selon la politique de rétention (ADR 0024). N'échoue jamais
 * bruyamment : une purge ratée n'invalide pas une collecte réussie, elle est simplement journalisée.
 */
@Component
@Order(4)
class RetentionRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionRunner.class);

    private final RetentionProperties properties;
    private final ObjectProvider<PostgresRetention> retention;
    private final Clock clock;

    RetentionRunner(RetentionProperties properties, ObjectProvider<PostgresRetention> retention, Clock clock) {
        this.properties = properties;
        this.retention = retention;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        PostgresRetention target = retention.getIfAvailable();
        if (!properties.enabled() || target == null) {
            return;
        }
        try {
            PostgresRetention.Result result = target.purge(properties.toPolicy(), clock.instant());
            LOG.info("Rétention : {} ligne(s) supprimée(s) ({} doublon(s) du jour, {} recommandation(s) de profils secondaires > {} j, "
                            + "{} du profil de référence > {} j, {} échec(s) de collecte > {} j)",
                    result.total(), result.sameDayDuplicates(), result.otherProfiles(), properties.otherProfilesRecommendations().toDays(),
                    result.defaultProfile(), properties.defaultProfileRecommendations().toDays(), result.failures(), properties.failures().toDays());
        } catch (RuntimeException e) {
            LOG.warn("Rétention non appliquée : {}", e.getMessage());
        }
    }
}
