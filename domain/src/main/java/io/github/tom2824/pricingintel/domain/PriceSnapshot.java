package io.github.tom2824.pricingintel.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Un relevé de prix : ce qu'une annonce affichait à un instant donné.
 * Immuable. Un relevé ne se corrige pas, on en produit un nouveau.
 *
 * @param listPrice    prix barré (avant promotion) si affiché, sinon null
 * @param shippingCost frais de port si affichés, sinon null
 */
public record PriceSnapshot(
        ListingId listingId,
        Instant observedAt,
        URI observedUrl,
        Money price,
        Money listPrice,
        Money shippingCost,
        Availability availability,
        ItemCondition condition,
        SellerType sellerType,
        ObservedIdentity identity,
        Extraction extraction) {

    public PriceSnapshot {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(observedUrl, "observedUrl");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(extraction, "extraction");
        if (!price.isPositive()) {
            throw new IllegalArgumentException("price must be positive, got " + price);
        }
        availability = availability == null ? Availability.UNKNOWN : availability;
        condition = condition == null ? ItemCondition.UNKNOWN : condition;
        sellerType = sellerType == null ? SellerType.UNKNOWN : sellerType;
        identity = identity == null ? ObservedIdentity.EMPTY : identity;
    }

    public boolean isDiscounted() {
        return listPrice != null && listPrice.isGreaterThan(price);
    }

    public Optional<Money> listPriceIfAny() {
        return Optional.ofNullable(listPrice);
    }

    public Optional<Money> shippingCostIfAny() {
        return Optional.ofNullable(shippingCost);
    }

    public static Builder builder(ListingId listingId, Instant observedAt, URI observedUrl, Money price) {
        return new Builder(listingId, observedAt, observedUrl, price);
    }

    /** Constructeur fluide pour les champs optionnels ; les quatre champs obligatoires sont exigés d'entrée. */
    public static final class Builder {
        private final ListingId listingId;
        private final Instant observedAt;
        private final URI observedUrl;
        private final Money price;
        private Money listPrice;
        private Money shippingCost;
        private Availability availability = Availability.UNKNOWN;
        private ItemCondition condition = ItemCondition.UNKNOWN;
        private SellerType sellerType = SellerType.UNKNOWN;
        private ObservedIdentity identity = ObservedIdentity.EMPTY;
        private Extraction extraction = new Extraction("unspecified", 0.5);

        private Builder(ListingId listingId, Instant observedAt, URI observedUrl, Money price) {
            this.listingId = listingId;
            this.observedAt = observedAt;
            this.observedUrl = observedUrl;
            this.price = price;
        }

        public Builder listPrice(Money value) {
            this.listPrice = value;
            return this;
        }

        public Builder shippingCost(Money value) {
            this.shippingCost = value;
            return this;
        }

        public Builder availability(Availability value) {
            this.availability = value;
            return this;
        }

        public Builder condition(ItemCondition value) {
            this.condition = value;
            return this;
        }

        public Builder sellerType(SellerType value) {
            this.sellerType = value;
            return this;
        }

        public Builder identity(ObservedIdentity value) {
            this.identity = value;
            return this;
        }

        public Builder extraction(Extraction value) {
            this.extraction = value;
            return this;
        }

        public Builder extraction(String method, double confidence) {
            return extraction(new Extraction(method, confidence));
        }

        public PriceSnapshot build() {
            return new PriceSnapshot(listingId, observedAt, observedUrl, price, listPrice, shippingCost,
                    availability, condition, sellerType, identity, extraction);
        }
    }
}
