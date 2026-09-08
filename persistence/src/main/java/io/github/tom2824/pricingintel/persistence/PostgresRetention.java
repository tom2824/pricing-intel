package io.github.tom2824.pricingintel.persistence;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rétention en base, alignée sur le quota du fournisseur (ADR 0024). Les relevés, les décisions et les exécutions
 * sont conservés sans limite : ils sont petits et ils sont l'historique. Les recommandations, lourdes de leur
 * marché et de leur explication en JSON, sont élaguées : une par jour et par profil, les profils secondaires sur
 * une fenêtre courte, le profil de référence sur une fenêtre longue, la dernière de chaque profil toujours gardée.
 */
public class PostgresRetention {

    private final JdbcClient jdbc;

    public PostgresRetention(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public record Policy(Duration defaultProfileRecommendations, Duration otherProfilesRecommendations, Duration failures) {
    }

    public record Result(int sameDayDuplicates, int otherProfiles, int defaultProfile, int failures) {
        public int total() {
            return sameDayDuplicates + otherProfiles + defaultProfile + failures;
        }
    }

    @Transactional
    public Result purge(Policy policy, Instant now) {
        // 1. Plusieurs exécutions le même jour : seule la dernière recommandation du jour compte.
        int duplicates = jdbc.sql("""
                        delete from recommendation r
                        using recommendation later
                        where later.product_id = r.product_id and later.scope = r.scope and later.profile_key = r.profile_key
                          and later.computed_at::date = r.computed_at::date and later.computed_at > r.computed_at
                        """).update();
        // 2. Profils secondaires : fenêtre courte, mais jamais la dernière de chaque profil (vue recommendation_latest).
        int others = jdbc.sql("""
                        delete from recommendation r
                        where not r.is_default and r.computed_at < :before
                          and exists (select 1 from recommendation newer
                                      where newer.product_id = r.product_id and newer.scope = r.scope
                                        and newer.profile_key = r.profile_key and newer.computed_at > r.computed_at)
                        """)
                .param("before", now.minus(policy.otherProfilesRecommendations()).atOffset(ZoneOffset.UTC))
                .update();
        // 3. Profil de référence : fenêtre longue, même garde-fou.
        int defaults = jdbc.sql("""
                        delete from recommendation r
                        where r.is_default and r.computed_at < :before
                          and exists (select 1 from recommendation newer
                                      where newer.product_id = r.product_id and newer.scope = r.scope
                                        and newer.profile_key = r.profile_key and newer.computed_at > r.computed_at)
                        """)
                .param("before", now.minus(policy.defaultProfileRecommendations()).atOffset(ZoneOffset.UTC))
                .update();
        // 4. Échecs de collecte : utiles pour expliquer un trou récent, pas au-delà.
        int failures = jdbc.sql("delete from collection_failure where occurred_at < :before")
                .param("before", now.minus(policy.failures()).atOffset(ZoneOffset.UTC))
                .update();
        return new Result(duplicates, others, defaults, failures);
    }
}
