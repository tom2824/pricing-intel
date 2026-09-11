package io.github.tom2824.pricingintel.persistence;

import io.github.tom2824.pricingintel.collector.Retention;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rétention en base, alignée sur le quota du fournisseur (ADR 0024). Les relevés, les décisions et les exécutions
 * sont conservés sans limite : ils sont petits et ils sont l'historique. Les recommandations, lourdes de leur
 * marché et de leur explication en JSON, sont élaguées : les profils secondaires sur une fenêtre courte, le profil
 * de référence sur une fenêtre longue, la dernière de chaque profil toujours gardée. L'unicité par jour est
 * garantie par la table elle-même (une recommandation par jour et par profil, la première exécution fait foi).
 */
public class PostgresRetention implements Retention {

    private final JdbcClient jdbc;

    public PostgresRetention(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional
    public Result purge(Policy policy, Instant now) {
        // 1. Profils secondaires : fenêtre courte, mais jamais la dernière de chaque profil (vue recommendation_latest).
        int others = jdbc.sql("""
                        delete from recommendation r
                        where not r.is_default and r.computed_at < :before
                          and exists (select 1 from recommendation newer
                                      where newer.product_id = r.product_id and newer.scope = r.scope
                                        and newer.profile_key = r.profile_key and newer.computed_at > r.computed_at)
                        """)
                .param("before", now.minus(policy.otherProfilesRecommendations()).atOffset(ZoneOffset.UTC))
                .update();
        // 2. Profil de référence : fenêtre longue, même garde-fou.
        int defaults = jdbc.sql("""
                        delete from recommendation r
                        where r.is_default and r.computed_at < :before
                          and exists (select 1 from recommendation newer
                                      where newer.product_id = r.product_id and newer.scope = r.scope
                                        and newer.profile_key = r.profile_key and newer.computed_at > r.computed_at)
                        """)
                .param("before", now.minus(policy.defaultProfileRecommendations()).atOffset(ZoneOffset.UTC))
                .update();
        // 3. Profil de référence, au-delà de la fenêtre courte : on garde les chiffres et la phrase d'explication
        //    (l'historique du prix conseillé), on lâche le marché détaillé et les étapes, qui ne servent qu'au jour le jour.
        int compacted = jdbc.sql("""
                        update recommendation r
                        set market = jsonb_build_object('scope', r.market -> 'scope', 'asOf', r.market -> 'asOf',
                                                        'sourceCount', r.market -> 'sourceCount', 'min', r.market -> 'min',
                                                        'median', r.market -> 'median', 'mean', r.market -> 'mean', 'max', r.market -> 'max',
                                                        'compacted', true),
                            explanation = jsonb_build_object('text', r.explanation -> 'text'),
                            profile = jsonb_build_object('strategy', r.profile -> 'strategy', 'description', r.profile -> 'description')
                        where r.is_default and r.computed_at < :before
                          and not coalesce((r.market ->> 'compacted')::boolean, false)
                          and exists (select 1 from recommendation newer
                                      where newer.product_id = r.product_id and newer.scope = r.scope
                                        and newer.profile_key = r.profile_key and newer.computed_at > r.computed_at)
                        """)
                .param("before", now.minus(policy.otherProfilesRecommendations()).atOffset(ZoneOffset.UTC))
                .update();
        // 4. Échecs de collecte : utiles pour expliquer un trou récent, pas au-delà.
        int failures = jdbc.sql("delete from collection_failure where occurred_at < :before")
                .param("before", now.minus(policy.failures()).atOffset(ZoneOffset.UTC))
                .update();
        return new Result(others, defaults, compacted, failures);
    }
}
