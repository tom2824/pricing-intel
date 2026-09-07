package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.ItemCondition;
import io.github.tom2824.pricingintel.domain.ListingId;
import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.ProductId;
import io.github.tom2824.pricingintel.domain.SellerType;
import io.github.tom2824.pricingintel.domain.SourceId;
import java.time.Instant;
import java.util.Objects;

/**
 * Une offre concurrente telle qu'elle entre dans la construction du marché : le dernier relevé d'une annonce,
 * avec ce qu'il faut pour appliquer les règles (fraîcheur, stock, état, vendeur, quarantaine).
 *
 * @param product     produit apparié à l'annonce (utile en périmètre segment, où plusieurs produits se mélangent)
 * @param quarantined vrai si le relevé est suspect (ADR 0017)
 * @param confidence  confiance de l'extraction, entre 0 et 1
 */
public record ObservedOffer(
        SourceId source,
        ListingId listing,
        ProductId product,
        Money price,
        Instant observedAt,
        Availability availability,
        ItemCondition condition,
        SellerType sellerType,
        boolean quarantined,
        double confidence) {

    public ObservedOffer {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(observedAt, "observedAt");
        availability = availability == null ? Availability.UNKNOWN : availability;
        condition = condition == null ? ItemCondition.UNKNOWN : condition;
        sellerType = sellerType == null ? SellerType.UNKNOWN : sellerType;
    }
}
