package io.github.tom2824.pricingintel.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * La purge (ADR 0024) sur un vrai PostgreSQL : c'est le seul code qui détruit de l'historique, il est testé
 * chemin par chemin, avec des lignes à J-400, J-30, J-1 et J, et le contenu compacté vérifié.
 * Base rafraîchie avant la classe pour ne pas partager les lignes des autres tests.
 */
@SpringBootTest
@ActiveProfiles("postgres")
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY,
        refresh = AutoConfigureEmbeddedDatabase.RefreshMode.BEFORE_CLASS)
class PostgresRetentionTest {

    private static final Instant NOW = PersistenceTestApplication.NOW;
    private static final PostgresRetention.Policy POLICY =
            new PostgresRetention.Policy(Duration.ofDays(365), Duration.ofDays(7), Duration.ofDays(180));

    @Autowired
    PostgresRetention retention;

    @Autowired
    JdbcClient jdbc;

    private long tracked;
    private long other;

    @BeforeEach
    void seed() {
        jdbc.sql("delete from collection_failure").update();
        jdbc.sql("delete from collection_run").update();
        jdbc.sql("delete from recommendation").update();
        jdbc.sql("delete from product").update();
        tracked = product("suivi");
        other = product("autre");

        // Profil de référence du produit suivi : quatre jours d'historique, du plus ancien au plus récent.
        recommendation(tracked, "index-98", true, NOW.minus(Duration.ofDays(400)));
        recommendation(tracked, "index-98", true, NOW.minus(Duration.ofDays(30)));
        recommendation(tracked, "index-98", true, NOW.minus(Duration.ofDays(1)));
        recommendation(tracked, "index-98", true, NOW);
        // Profil secondaire : au-delà de la fenêtre courte, dedans, aujourd'hui.
        recommendation(tracked, "align", false, NOW.minus(Duration.ofDays(30)));
        recommendation(tracked, "align", false, NOW.minus(Duration.ofDays(1)));
        recommendation(tracked, "align", false, NOW);
        // L'autre produit n'a qu'une recommandation, très ancienne : c'est sa dernière, elle doit survivre intacte.
        recommendation(other, "index-98", true, NOW.minus(Duration.ofDays(400)));
        recommendation(other, "align", false, NOW.minus(Duration.ofDays(400)));

        long run = jdbc.sql("insert into collection_run (started_at, finished_at, attempted, collected, failed) values (:t, :t, 1, 0, 1) returning id")
                .param("t", NOW.atOffset(ZoneOffset.UTC)).query(Long.class).single();
        failure(run, NOW.minus(Duration.ofDays(200)));
        failure(run, NOW.minus(Duration.ofDays(1)));
    }

    @Test
    void purgesEachWindowAndNeverTheLatestOfAProfile() {
        PostgresRetention.Result result = retention.purge(POLICY, NOW);

        assertThat(result.defaultProfile()).as("référence > 365 j, sauf la dernière").isEqualTo(1);
        assertThat(result.otherProfiles()).as("secondaires > 7 j, sauf la dernière").isEqualTo(1);
        assertThat(result.compacted()).as("référence > 7 j encore gardée").isEqualTo(1);
        assertThat(result.failures()).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(3);

        assertThat(days(tracked, "index-98")).containsExactly(30L, 1L, 0L);
        assertThat(days(tracked, "align")).containsExactly(1L, 0L);
        assertThat(days(other, "index-98")).containsExactly(400L);
        assertThat(days(other, "align")).containsExactly(400L);
        assertThat(jdbc.sql("select count(*) from collection_failure").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("select count(*) from recommendation_latest").query(Long.class).single()).isEqualTo(4L);
    }

    @Test
    void compactionKeepsTheNumbersAndTheSentenceOnly() {
        retention.purge(POLICY, NOW);

        String compacted = jdbc.sql("select market::text from recommendation where product_id = :p and profile_key = 'index-98' and computed_at < :t")
                .param("p", tracked).param("t", NOW.minus(Duration.ofDays(7)).atOffset(ZoneOffset.UTC))
                .query(String.class).single();
        assertThat(compacted).contains("\"compacted\": true").contains("\"median\": 979.95").contains("\"sourceCount\": 3")
                .doesNotContain("retained").doesNotContain("excluded");
        assertThat(jdbc.sql("select explanation::text from recommendation where product_id = :p and profile_key = 'index-98' and computed_at < :t")
                .param("p", tracked).param("t", NOW.minus(Duration.ofDays(7)).atOffset(ZoneOffset.UTC)).query(String.class).single())
                .contains("marché strict").doesNotContain("steps");
        assertThat(jdbc.sql("select profile::text from recommendation where product_id = :p and profile_key = 'index-98' and computed_at < :t")
                .param("p", tracked).param("t", NOW.minus(Duration.ofDays(7)).atOffset(ZoneOffset.UTC)).query(String.class).single())
                .contains("\"strategy\": \"index\"").doesNotContain("minSources");

        // Les lignes récentes gardent tout, et la dernière de l'autre produit n'a pas été touchée malgré son âge.
        assertThat(jdbc.sql("select count(*) from recommendation where jsonb_exists(market, 'retained')").query(Long.class).single()).isEqualTo(6L);
        assertThat(jdbc.sql("select jsonb_exists(market, 'retained') from recommendation where product_id = :p and profile_key = 'index-98'")
                .param("p", other).query(Boolean.class).single()).isTrue();
    }

    @Test
    void secondPurgeChangesNothing() {
        retention.purge(POLICY, NOW);

        PostgresRetention.Result again = retention.purge(POLICY, NOW);

        assertThat(again).isEqualTo(new PostgresRetention.Result(0, 0, 0, 0));
    }

    @Test
    void refusesAWindowThatWouldPurgeEverything() {
        assertThatThrownBy(() -> new PostgresRetention.Policy(Duration.ofDays(365), Duration.ofDays(7), Duration.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("failures");
        assertThatThrownBy(() -> new PostgresRetention.Policy(Duration.ZERO, Duration.ofDays(7), Duration.ofDays(180)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultProfileRecommendations");
    }

    private java.util.List<Long> days(long productId, String profile) {
        return jdbc.sql("select computed_at from recommendation where product_id = :p and profile_key = :k order by computed_at")
                .param("p", productId).param("k", profile)
                .query(java.time.OffsetDateTime.class).list().stream()
                .map(t -> Duration.between(t.toInstant(), NOW).toDays())
                .toList();
    }

    private long product(String key) {
        return jdbc.sql("insert into product (family_code, brand, name, natural_key) values ('gpu', 'Test', :name, :key) returning id")
                .param("name", "Produit " + key).param("key", "test-" + key)
                .query(Long.class).single();
    }

    private void recommendation(long productId, String profileKey, boolean isDefault, Instant computedAt) {
        jdbc.sql("""
                        insert into recommendation (product_id, computed_at, computed_date, scope, profile_key, is_default, strategy, fell_back,
                                                    profile, price, currency, index_vs_median, market, explanation)
                        values (:product_id, :computed_at, :computed_date, 'strict', :profile_key, :is_default, 'index', false,
                                cast(:profile as jsonb), 959.99, 'EUR', 98.0, cast(:market as jsonb), cast(:explanation as jsonb))
                        """)
                .param("product_id", productId)
                .param("computed_at", computedAt.atOffset(ZoneOffset.UTC))
                .param("computed_date", computedAt.atOffset(ZoneOffset.UTC).toLocalDate())
                .param("profile_key", profileKey)
                .param("is_default", isDefault)
                .param("profile", "{\"strategy\": \"index\", \"description\": \"index 98 sur la médiane\", \"minSources\": 2}")
                .param("market", "{\"scope\": \"strict\", \"asOf\": \"" + computedAt + "\", \"sourceCount\": 3, \"min\": 959.99, "
                        + "\"median\": 979.95, \"mean\": 973.30, \"max\": 979.95, "
                        + "\"retained\": [{\"source\": \"ldlc\", \"price\": 979.95}], \"excluded\": []}")
                .param("explanation", "{\"text\": \"marché strict : 3 offres · index 98 → 959.99\", "
                        + "\"steps\": [{\"stage\": \"market\", \"label\": \"marché\"}]}")
                .update();
    }

    private void failure(long runId, Instant occurredAt) {
        jdbc.sql("""
                        insert into collection_failure (run_id, listing_code, source_id, reason, retryable, occurred_at)
                        values (:run, 'listing-x', 'scraper', 'HTTP 503', true, :at)
                        """)
                .param("run", runId).param("at", occurredAt.atOffset(ZoneOffset.UTC)).update();
    }
}
