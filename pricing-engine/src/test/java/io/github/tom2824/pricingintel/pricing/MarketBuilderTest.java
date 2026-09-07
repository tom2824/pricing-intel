package io.github.tom2824.pricingintel.pricing;

import static io.github.tom2824.pricingintel.pricing.Markets.EUR;
import static io.github.tom2824.pricingintel.pricing.Markets.MSI_VENTUS;
import static io.github.tom2824.pricingintel.pricing.Markets.NOW;
import static io.github.tom2824.pricingintel.pricing.Markets.offer;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.ItemCondition;
import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.SellerType;
import io.github.tom2824.pricingintel.domain.SourceId;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketBuilderTest {

    @Test
    void keepsInStockFreshOffersAndExplainsEachExclusion() {
        MarketView market = Markets.realMarket();

        assertThat(market.retained()).extracting(o -> o.source().value()).containsExactly("topachat", "ldlc", "materiel-net");
        assertThat(market.excluded()).singleElement().satisfies(e -> {
            assertThat(e.offer().source().value()).isEqualTo("cybertek");
            assertThat(e.reason()).isEqualTo("rupture");
        });
        assertThat(market.sourceCount()).isEqualTo(3);
        assertThat(market.min()).contains(Money.eur("959.99"));
        assertThat(market.median()).contains(Money.eur("979.95"));
        assertThat(market.max()).contains(Money.eur("979.95"));
        assertThat(market.mean().orElseThrow().amount()).isEqualByComparingTo("973.30");
        assertThat(market.summary()).isEqualTo("3 offre(s) de 3 enseigne(s), min 959.99 EUR, médiane 979.95 EUR, max 979.95 EUR, 1 écartée(s)");
    }

    @Test
    void medianAveragesTheTwoMiddleOffersForAnEvenCount() {
        MarketView market = MarketBuilder.withDefaults().build(MSI_VENTUS, MarketScope.STRICT, NOW, EUR,
                List.of(offer("a", "100"), offer("b", "110"), offer("c", "130"), offer("d", "200")));

        assertThat(market.median().orElseThrow().amount()).isEqualByComparingTo("120");
    }

    @Test
    void appliesFreshnessConditionQuarantineAndMarketplaceRules() {
        List<ObservedOffer> offers = List.of(
                offer("stale", "900", NOW.minus(Duration.ofHours(49)), Availability.IN_STOCK, ItemCondition.NEW, SellerType.DIRECT, false),
                offer("refurb", "800", NOW, Availability.IN_STOCK, ItemCondition.REFURBISHED, SellerType.DIRECT, false),
                offer("suspect", "96", NOW, Availability.IN_STOCK, ItemCondition.NEW, SellerType.DIRECT, true),
                offer("market", "850", NOW, Availability.IN_STOCK, ItemCondition.NEW, SellerType.MARKETPLACE, false),
                offer("preorder", "950", NOW, Availability.PREORDER, ItemCondition.NEW, SellerType.DIRECT, false),
                offer("fine", "960", NOW, Availability.IN_STOCK, ItemCondition.UNKNOWN, SellerType.UNKNOWN, false));

        MarketView market = MarketBuilder.withDefaults().build(MSI_VENTUS, MarketScope.STRICT, NOW, EUR, offers);

        assertThat(market.retained()).extracting(o -> o.source().value()).containsExactly("fine");
        assertThat(market.excluded()).extracting(ExcludedOffer::reason).containsExactlyInAnyOrder(
                "relevé périmé (49 h, limite 48 h)", "reconditionné", "relevé en quarantaine", "vendeur marketplace", "précommande");

        MarketView relaxed = new MarketBuilder(MarketRules.defaults().includingRefurbished()
                .withExcludeMarketplace(false).withExcludeQuarantined(false).withInStockOnly(false))
                .build(MSI_VENTUS, MarketScope.STRICT, NOW, EUR, offers);
        assertThat(relaxed.retained()).hasSize(5);
    }

    @Test
    void keepsOneOfferPerSourceTheCheapestOne() {
        List<ObservedOffer> offers = List.of(
                new ObservedOffer(new SourceId("ldlc"), new io.github.tom2824.pricingintel.domain.ListingId("ldlc-a"), MSI_VENTUS,
                        Money.eur("980"), NOW, Availability.IN_STOCK, ItemCondition.NEW, SellerType.DIRECT, false, 0.9),
                new ObservedOffer(new SourceId("ldlc"), new io.github.tom2824.pricingintel.domain.ListingId("ldlc-b"), MSI_VENTUS,
                        Money.eur("970"), NOW, Availability.IN_STOCK, ItemCondition.NEW, SellerType.DIRECT, false, 0.9));

        MarketView market = MarketBuilder.withDefaults().build(MSI_VENTUS, MarketScope.STRICT, NOW, EUR, offers);

        assertThat(market.retained()).singleElement().satisfies(o -> assertThat(o.listing().value()).isEqualTo("ldlc-b"));
        assertThat(market.excluded()).singleElement().satisfies(e -> assertThat(e.reason()).startsWith("doublon enseigne"));
        assertThat(market.sourceCount()).isEqualTo(1);
    }

    @Test
    void emptyMarketHasNoAggregates() {
        MarketView market = MarketBuilder.withDefaults().build(MSI_VENTUS, MarketScope.STRICT, NOW, EUR, List.of());

        assertThat(market.isEmpty()).isTrue();
        assertThat(market.min()).isEmpty();
        assertThat(market.median()).isEmpty();
        assertThat(market.summary()).isEqualTo("aucune offre retenue (0 écartée(s))");
    }
}
