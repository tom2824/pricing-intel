package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.ProductId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * Port de sortie : où va la décision tarifaire du jour (ADR 0023). Une seule par produit et par jour ; une
 * seconde tentative le même jour ne fait rien et le dit.
 */
public interface PriceDecisionStore {

    /**
     * @param oldPrice prix courant avant la décision, {@code null} si le produit n'en avait pas
     * @param newPrice prix courant après la décision, égal à l'ancien quand elle ne change rien
     * @param changed  vrai si la décision modifie le prix courant
     */
    record PriceDecision(ProductId product, LocalDate date, Instant decidedAt, BigDecimal oldPrice, BigDecimal newPrice,
                         Currency currency, String profileKey, String strategy, boolean changed, String reason) {
        public PriceDecision {
            Objects.requireNonNull(product, "product");
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(decidedAt, "decidedAt");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(profileKey, "profileKey");
            Objects.requireNonNull(strategy, "strategy");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** @return {@code true} si la décision a été enregistrée, {@code false} si le produit avait déjà sa décision du jour */
    boolean record(PriceDecision decision);
}
