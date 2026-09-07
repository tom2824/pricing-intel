package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.ProductId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Le résultat du moteur : un prix proposé (ou aucun, si même le repli est impossible), la stratégie qui a
 * réellement servi, l'explication complète, le marché sur lequel elle repose et le profil utilisé.
 *
 * @param profileKey     nom du profil (plusieurs profils sont précalculés pour le simulateur du portfolio)
 * @param defaultProfile vrai pour le profil de référence, celui des vues de synthèse
 * @param strategyId     stratégie effectivement appliquée ({@code hold} si le moteur s'est replié)
 * @param fellBack       vrai si la stratégie demandée n'a pas pu s'appliquer
 */
public record Recommendation(
        ProductId product,
        Instant computedAt,
        MarketView market,
        PricingProfile profile,
        String profileKey,
        boolean defaultProfile,
        String strategyId,
        boolean fellBack,
        Money price,
        Explanation explanation) {

    public Recommendation {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(computedAt, "computedAt");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(profileKey, "profileKey");
        Objects.requireNonNull(strategyId, "strategyId");
        Objects.requireNonNull(explanation, "explanation");
    }

    public Optional<Money> priceIfAny() {
        return Optional.ofNullable(price);
    }

    /** Position de notre prix proposé par rapport à la médiane, en pourcentage (98.00 = 2 % sous la médiane). */
    public Optional<BigDecimal> indexVersusMedian() {
        if (price == null) {
            return Optional.empty();
        }
        return market.median().map(price::ratioPercent);
    }
}
