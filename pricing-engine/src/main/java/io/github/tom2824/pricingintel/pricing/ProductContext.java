package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.ProductId;
import io.github.tom2824.pricingintel.domain.SourceId;
import java.util.Objects;
import java.util.Optional;

/**
 * Ce que le moteur sait du produit lui-même, indépendamment du marché.
 *
 * @param currentPrice  notre prix actuel, ou null si le produit n'est pas encore vendu
 * @param purchasePrice prix d'achat (fictif dans la démonstration), ou null
 * @param leader        enseigne de référence pour la stratégie « suivi du leader », ou null
 */
public record ProductContext(ProductId id, String name, Money currentPrice, Money purchasePrice, SourceId leader) {

    public ProductContext {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }

    public Optional<Money> currentPriceIfAny() {
        return Optional.ofNullable(currentPrice);
    }

    public Optional<Money> purchasePriceIfAny() {
        return Optional.ofNullable(purchasePrice);
    }

    public Optional<SourceId> leaderIfAny() {
        return Optional.ofNullable(leader);
    }
}
