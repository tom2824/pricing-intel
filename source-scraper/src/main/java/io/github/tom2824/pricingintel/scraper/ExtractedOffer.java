package io.github.tom2824.pricingintel.scraper;

import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.ItemCondition;
import io.github.tom2824.pricingintel.domain.ObservedIdentity;
import io.github.tom2824.pricingintel.domain.SellerType;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Ce qu'un extracteur a lu dans une page, avant conversion en relevé. Le prix et la devise sont obligatoires ;
 * le reste est à {@code null} ou {@code UNKNOWN} quand la page ne le dit pas.
 */
public record ExtractedOffer(
        BigDecimal price,
        BigDecimal listPrice,
        String currency,
        Availability availability,
        ItemCondition condition,
        SellerType sellerType,
        ObservedIdentity identity,
        String method,
        double confidence) {

    public ExtractedOffer {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(method, "method");
        availability = availability == null ? Availability.UNKNOWN : availability;
        condition = condition == null ? ItemCondition.UNKNOWN : condition;
        sellerType = sellerType == null ? SellerType.UNKNOWN : sellerType;
        identity = identity == null ? ObservedIdentity.EMPTY : identity;
    }

    public static Builder builder(BigDecimal price, String currency, String method, double confidence) {
        return new Builder(price, currency, method, confidence);
    }

    public static final class Builder {
        private final BigDecimal price;
        private final String currency;
        private final String method;
        private final double confidence;
        private BigDecimal listPrice;
        private Availability availability;
        private ItemCondition condition;
        private SellerType sellerType;
        private ObservedIdentity identity;

        private Builder(BigDecimal price, String currency, String method, double confidence) {
            this.price = price;
            this.currency = currency;
            this.method = method;
            this.confidence = confidence;
        }

        public Builder listPrice(BigDecimal value) {
            this.listPrice = value;
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

        public ExtractedOffer build() {
            return new ExtractedOffer(price, listPrice, currency, availability, condition, sellerType, identity, method, confidence);
        }
    }
}
