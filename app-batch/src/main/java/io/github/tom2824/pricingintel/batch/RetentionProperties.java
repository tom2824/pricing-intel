package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.persistence.PostgresRetention;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rétention en base (ADR 0024), sous le préfixe {@code retention}. Les relevés, décisions et exécutions ne sont
 * jamais purgés ; seules les recommandations et les échecs de collecte le sont.
 */
@ConfigurationProperties(prefix = "retention")
public record RetentionProperties(
        @DefaultValue("true") boolean enabled,
        /** Recommandations du profil de référence : fenêtre longue, c'est l'historique du prix conseillé. */
        @DefaultValue("365d") Duration defaultProfileRecommendations,
        /** Recommandations des autres profils : fenêtre courte, le simulateur ne regarde que le présent. */
        @DefaultValue("7d") Duration otherProfilesRecommendations,
        @DefaultValue("180d") Duration failures) {

    public PostgresRetention.Policy toPolicy() {
        return new PostgresRetention.Policy(defaultProfileRecommendations, otherProfilesRecommendations, failures);
    }
}
