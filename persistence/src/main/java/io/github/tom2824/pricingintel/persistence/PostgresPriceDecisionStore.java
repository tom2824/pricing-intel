package io.github.tom2824.pricingintel.persistence;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enregistre la décision tarifaire du jour d'un produit et, si elle change le prix, met à jour le prix courant
 * (ADR 0023). Une seule décision par produit et par jour : une seconde exécution le même jour ne fait rien.
 */
public class PostgresPriceDecisionStore {

    private final JdbcClient jdbc;

    public PostgresPriceDecisionStore(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public record Decision(long productId, LocalDate date, Instant decidedAt, BigDecimal oldPrice, BigDecimal newPrice,
                           String currency, String profileKey, String strategy, boolean changed, String reason) {
    }

    /** @return {@code true} si la décision a été enregistrée, {@code false} si le produit avait déjà sa décision du jour */
    @Transactional
    public boolean record(Decision d) {
        int inserted = jdbc.sql("""
                        insert into product_price_decision (product_id, decision_date, decided_at, old_price, new_price, currency,
                                                            profile_key, strategy, changed, reason)
                        values (:product_id, :decision_date, :decided_at, :old_price, :new_price, :currency,
                                :profile_key, :strategy, :changed, :reason)
                        on conflict (product_id, decision_date) do nothing
                        """)
                .param("product_id", d.productId())
                .param("decision_date", d.date())
                .param("decided_at", d.decidedAt().atOffset(ZoneOffset.UTC))
                .param("old_price", d.oldPrice(), Types.NUMERIC)
                .param("new_price", d.newPrice(), Types.NUMERIC)
                .param("currency", d.currency())
                .param("profile_key", d.profileKey())
                .param("strategy", d.strategy())
                .param("changed", d.changed())
                .param("reason", d.reason())
                .update();
        if (inserted == 0) {
            return false;
        }
        if (d.changed()) {
            jdbc.sql("update product set current_price = :price where id = :id")
                    .param("price", d.newPrice(), Types.NUMERIC)
                    .param("id", d.productId())
                    .update();
        }
        return true;
    }
}
