package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.collector.Retention;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Rétention en base (ADR 0024), sous le préfixe {@code retention}. Les relevés, décisions et exécutions ne sont
 * jamais purgés ; seules les recommandations et les échecs de collecte le sont. Une fenêtre nulle ou négative est
 * refusée au démarrage : elle purgerait tout.
 */
@Validated
@ConfigurationProperties(prefix = "retention")
public record RetentionProperties(
        @DefaultValue("true") boolean enabled,
        /** Recommandations du profil de référence : fenêtre longue, c'est l'historique du prix conseillé. */
        @NotNull @DurationMin(days = 1) @DefaultValue("365d") Duration defaultProfileRecommendations,
        /** Recommandations des autres profils : fenêtre courte, le simulateur ne regarde que le présent. */
        @NotNull @DurationMin(days = 1) @DefaultValue("7d") Duration otherProfilesRecommendations,
        @NotNull @DurationMin(days = 1) @DefaultValue("180d") Duration failures) {

    public Retention.Policy toPolicy() {
        return new Retention.Policy(defaultProfileRecommendations, otherProfilesRecommendations, failures);
    }
}
