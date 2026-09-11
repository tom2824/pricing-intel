package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.ProductId;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Port d'entrée du moteur : ce qu'il lit avant de calculer. Les produits actifs avec leur contexte, et le dernier
 * relevé de chaque annonce du périmètre d'un produit (strict : le produit ; segment : ses équivalents). Les règles
 * du marché ne s'appliquent pas ici mais dans le moteur, qui explique chaque exclusion (ADR 0020).
 */
public interface MarketOffers {

    /** Un produit à tarifer : sa famille, sa devise, et le contexte que le moteur consomme (prix courant, prix d'achat). */
    record ActiveProduct(ProductId id, String family, Currency currency, ProductContext context) {
        public ActiveProduct {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(context, "context");
        }
    }

    List<ActiveProduct> activeProducts();

    /** Les relevés observés depuis {@code since}, un par annonce (le plus récent), sur le périmètre demandé. */
    List<ObservedOffer> latestOffers(ProductId product, MarketScope scope, Instant since);
}
