package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.ItemCondition;
import io.github.tom2824.pricingintel.domain.ProductId;
import io.github.tom2824.pricingintel.domain.SellerType;
import io.github.tom2824.pricingintel.domain.SourceId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applique les {@link MarketRules} à des offres observées et construit la {@link MarketView}.
 * Chaque offre écartée reçoit une raison en français, reprise telle quelle dans l'explication du prix.
 */
public final class MarketBuilder {

    private final MarketRules rules;

    public MarketBuilder(MarketRules rules) {
        this.rules = rules;
    }

    public static MarketBuilder withDefaults() {
        return new MarketBuilder(MarketRules.defaults());
    }

    /**
     * @param offers dernier relevé de chaque annonce du périmètre, toute fraîcheur confondue
     */
    public MarketView build(ProductId product, MarketScope scope, Instant asOf, Currency currency, List<ObservedOffer> offers) {
        List<ExcludedOffer> excluded = new ArrayList<>();
        Map<SourceId, ObservedOffer> bestBySource = new LinkedHashMap<>();

        for (ObservedOffer offer : offers) {
            Optional<String> rejection = reject(offer, asOf, currency);
            if (rejection.isPresent()) {
                excluded.add(new ExcludedOffer(offer, rejection.get()));
                continue;
            }
            ObservedOffer current = bestBySource.get(offer.source());
            if (current == null) {
                bestBySource.put(offer.source(), offer);
            } else if (offer.price().isLessThan(current.price())) {
                excluded.add(new ExcludedOffer(current, "doublon enseigne, offre plus chère que " + offer.listing()));
                bestBySource.put(offer.source(), offer);
            } else {
                excluded.add(new ExcludedOffer(offer, "doublon enseigne, offre plus chère que " + current.listing()));
            }
        }
        return new MarketView(product, scope, asOf, currency, new ArrayList<>(bestBySource.values()), excluded);
    }

    private Optional<String> reject(ObservedOffer offer, Instant asOf, Currency currency) {
        if (!offer.price().currency().equals(currency)) {
            return Optional.of("devise " + offer.price().currency().getCurrencyCode() + " non comparable");
        }
        Duration age = Duration.between(offer.observedAt(), asOf);
        if (age.compareTo(rules.freshness()) > 0) {
            return Optional.of("relevé périmé (" + age.toHours() + " h, limite " + rules.freshness().toHours() + " h)");
        }
        if (rules.inStockOnly() && offer.availability() != Availability.IN_STOCK) {
            return Optional.of(switch (offer.availability()) {
                case OUT_OF_STOCK -> "rupture";
                case PREORDER -> "précommande";
                case BACKORDER -> "réapprovisionnement";
                default -> "disponibilité inconnue";
            });
        }
        if (offer.condition() != ItemCondition.UNKNOWN && !rules.conditions().contains(offer.condition())) {
            return Optional.of(switch (offer.condition()) {
                case REFURBISHED -> "reconditionné";
                case USED -> "occasion";
                default -> "état " + offer.condition();
            });
        }
        if (rules.excludeQuarantined() && offer.quarantined()) {
            return Optional.of("relevé en quarantaine");
        }
        if (rules.excludeMarketplace() && offer.sellerType() == SellerType.MARKETPLACE) {
            return Optional.of("vendeur marketplace");
        }
        return Optional.empty();
    }
}
