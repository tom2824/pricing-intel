package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.ItemCondition;
import io.github.tom2824.pricingintel.domain.ListingId;
import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.ProductId;
import io.github.tom2824.pricingintel.domain.SellerType;
import io.github.tom2824.pricingintel.domain.SourceId;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

/** Fabrique de marchés pour les tests, calquée sur les relevés réels du 6 septembre 2026 (MSI RTX 5070 Ventus). */
final class Markets {

    static final Instant NOW = Instant.parse("2026-09-07T08:00:00Z");
    static final ProductId MSI_VENTUS = new ProductId("1");
    static final Currency EUR = Currency.getInstance("EUR");

    private Markets() {
    }

    static ObservedOffer offer(String source, String price) {
        return offer(source, price, NOW.minusSeconds(3600), Availability.IN_STOCK, ItemCondition.NEW, SellerType.DIRECT, false);
    }

    static ObservedOffer offer(String source, String price, Instant observedAt, Availability availability,
                               ItemCondition condition, SellerType sellerType, boolean quarantined) {
        return new ObservedOffer(new SourceId(source), new ListingId(source + "-msi-ventus"), MSI_VENTUS,
                Money.eur(price), observedAt, availability, condition, sellerType, quarantined, 0.95);
    }

    /** LDLC 979,95 · Materiel.net 979,95 · TopAchat 959,99 ; Cybertek 894,99 en rupture. */
    static List<ObservedOffer> realOffers() {
        return List.of(
                offer("ldlc", "979.95"),
                offer("materiel-net", "979.95"),
                offer("topachat", "959.99"),
                offer("cybertek", "894.99", NOW.minusSeconds(3600), Availability.OUT_OF_STOCK, ItemCondition.NEW, SellerType.DIRECT, false));
    }

    static MarketView realMarket() {
        return MarketBuilder.withDefaults().build(MSI_VENTUS, MarketScope.STRICT, NOW, EUR, realOffers());
    }

    static ProductContext msiVentus() {
        return new ProductContext(MSI_VENTUS, "MSI GeForce RTX 5070 12G VENTUS 2X OC",
                Money.eur("969.99"), Money.eur("820"), new SourceId("ldlc"));
    }
}
