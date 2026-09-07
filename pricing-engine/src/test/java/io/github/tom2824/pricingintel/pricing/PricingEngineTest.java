package io.github.tom2824.pricingintel.pricing;

import static io.github.tom2824.pricingintel.pricing.Markets.EUR;
import static io.github.tom2824.pricingintel.pricing.Markets.MSI_VENTUS;
import static io.github.tom2824.pricingintel.pricing.Markets.NOW;
import static io.github.tom2824.pricingintel.pricing.Markets.msiVentus;
import static io.github.tom2824.pricingintel.pricing.Markets.offer;
import static io.github.tom2824.pricingintel.pricing.Markets.realMarket;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.SourceId;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingEngineTest {

    private final PricingEngine engine = new PricingEngine();

    @Test
    void reproducesTheAdrExampleStepByStep() {
        PricingProfile profile = PricingProfile.defaults().withMaxDailyMovePercent(new BigDecimal("3"));

        Recommendation r = engine.recommend(realMarket(), msiVentus(), profile);

        assertThat(r.price()).isEqualTo(Money.eur("959.99"));
        assertThat(r.strategyId()).isEqualTo("index");
        assertThat(r.fellBack()).isFalse();
        assertThat(r.indexVersusMedian()).contains(new BigDecimal("97.96"));
        assertThat(r.explanation().render()).isEqualTo(
                "marché strict : 3 offre(s) de 3 enseigne(s), min 959.99 EUR, médiane 979.95 EUR, max 979.95 EUR, 1 écartée(s)"
                        + " · index 98 % de la médiane : 979.95 EUR → 960.35 EUR"
                        + " · plancher marge 15 % (943.00 EUR) respecté → 960.35 EUR"
                        + " · plafond 110 % de la médiane (1077.95 EUR) respecté → 960.35 EUR"
                        + " · variation -1.0 % dans la limite de ±3 % → 960.35 EUR"
                        + " · arrondi ,99 vers le bas : 960.35 EUR → 959.99 EUR");
    }

    @Test
    void eachStrategyProposesItsOwnCandidate() {
        MarketView market = realMarket();
        ProductContext product = msiVentus();
        PricingProfile noRules = new PricingProfile(new PricingStrategy.AlignOnCheapest(), 0, null, null, null, null);

        assertThat(engine.recommend(market, product, noRules).price()).isEqualTo(Money.eur("959.99"));
        assertThat(engine.recommend(market, product, noRules.withStrategy(new PricingStrategy.Undercut(new BigDecimal("1")))).price())
                .isEqualTo(Money.eur("950.39"));
        assertThat(engine.recommend(market, product, noRules.withStrategy(PricingStrategy.TargetIndex.ofMedian("103"))).price())
                .isEqualTo(Money.eur("1009.35"));
        assertThat(engine.recommend(market, product, noRules.withStrategy(new PricingStrategy.TargetIndex(new BigDecimal("100"),
                PricingStrategy.TargetIndex.Reference.MEAN))).price()).isEqualTo(Money.eur("973.30"));
        assertThat(engine.recommend(market, product, noRules.withStrategy(new PricingStrategy.FollowLeader(new SourceId("ldlc"), new BigDecimal("-2")))).price())
                .isEqualTo(Money.eur("960.35"));
        assertThat(engine.recommend(market, product, noRules.withStrategy(new PricingStrategy.CostPlus(new BigDecimal("25")))).price())
                .isEqualTo(Money.eur("1025.00"));
        assertThat(engine.recommend(market, product, noRules.withStrategy(new PricingStrategy.Hold())).price())
                .isEqualTo(Money.eur("969.99"));
    }

    @Test
    void rulesClampAggressiveOrTimidCandidates() {
        MarketView market = realMarket();
        ProductContext product = msiVentus();

        Recommendation floored = engine.recommend(market, product, PricingProfile.defaults()
                .withStrategy(new PricingStrategy.Undercut(new BigDecimal("10"))).withMaxDailyMovePercent(null));
        assertThat(floored.price()).isEqualTo(Money.eur("942.99"));
        assertThat(floored.explanation().render()).contains("plancher marge 15 % (943.00 EUR) appliqué : 863.99 EUR → 943.00 EUR");

        Recommendation capped = engine.recommend(market, product, PricingProfile.defaults()
                .withStrategy(new PricingStrategy.CostPlus(new BigDecimal("40"))).withMaxDailyMovePercent(null));
        assertThat(capped.price()).isEqualTo(Money.eur("1076.99"));
        assertThat(capped.explanation().render()).contains("plafond 110 % de la médiane (1077.95 EUR) appliqué : 1148.00 EUR → 1077.95 EUR");

        Recommendation smoothed = engine.recommend(market, product, PricingProfile.defaults()
                .withStrategy(new PricingStrategy.Undercut(new BigDecimal("6"))).withMarginFloorPercent(null)
                .withMaxDailyMovePercent(new BigDecimal("2")));
        assertThat(smoothed.price()).isEqualTo(Money.eur("949.99"));
        assertThat(smoothed.explanation().render()).contains("variation -7.0 % limitée à ±2 % du prix actuel : 902.39 EUR → 950.59 EUR");
    }

    @Test
    void fallsBackToHoldWhenTheMarketIsTooThinOrTheStrategyCannotApply() {
        MarketView thin = MarketBuilder.withDefaults().build(MSI_VENTUS, MarketScope.STRICT, NOW, EUR, List.of(offer("ldlc", "979.95")));

        Recommendation r = engine.recommend(thin, msiVentus(), PricingProfile.defaults());
        assertThat(r.fellBack()).isTrue();
        assertThat(r.strategyId()).isEqualTo("hold");
        assertThat(r.price()).isEqualTo(Money.eur("969.99"));
        assertThat(r.explanation().render()).contains("sources insuffisantes (1 < 2) : repli sur le maintien");

        Recommendation noLeader = engine.recommend(realMarket(), msiVentus(), PricingProfile.defaults()
                .withStrategy(new PricingStrategy.FollowLeader(new SourceId("amazon"), null)));
        assertThat(noLeader.fellBack()).isTrue();
        assertThat(noLeader.explanation().render()).contains("suivi du leader impossible : amazon absent du marché retenu");
    }

    @Test
    void reportsWhenNothingCanBeProposed() {
        ProductContext unknown = new ProductContext(MSI_VENTUS, "Nouveau produit", null, null, null);
        MarketView empty = MarketBuilder.withDefaults().build(MSI_VENTUS, MarketScope.STRICT, NOW, EUR, List.of());

        Recommendation r = engine.recommend(empty, unknown, PricingProfile.defaults());

        assertThat(r.price()).isNull();
        assertThat(r.priceIfAny()).isEmpty();
        assertThat(r.explanation().render()).endsWith("aucun prix proposable");
    }

    @Test
    void roundingNeverGoesUp() {
        PricingProfile only99 = new PricingProfile(new PricingStrategy.AlignOnCheapest(), 0, null, null, null, 99);
        MarketView market = MarketBuilder.withDefaults().build(MSI_VENTUS, MarketScope.STRICT, NOW, EUR,
                List.of(offer("a", "100.00"), offer("b", "100.50")));

        assertThat(engine.recommend(market, msiVentus(), only99).price()).isEqualTo(Money.eur("99.99"));
        assertThat(engine.recommend(market, msiVentus(), only99.withRoundingCents(95)).price()).isEqualTo(Money.eur("99.95"));
    }
}
