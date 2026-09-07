package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.domain.SourceId;
import io.github.tom2824.pricingintel.pricing.MarketRules;
import io.github.tom2824.pricingintel.pricing.PricingProfile;
import io.github.tom2824.pricingintel.pricing.PricingStrategy;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Le profil de prix par défaut (ADR 0022) et les règles du marché (ADR 0020), sous le préfixe {@code pricing}.
 * Les valeurs par défaut sont celles des ADR ; tout se surcharge en ligne de commande ou par l'environnement.
 *
 * @param strategy index | align | undercut | leader | cost-plus | hold
 */
@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("index") String strategy,
        @DefaultValue("98") BigDecimal indexPercent,
        @DefaultValue("median") String indexReference,
        @DefaultValue("1") BigDecimal undercutPercent,
        String leaderSource,
        @DefaultValue("0") BigDecimal leaderOffsetPercent,
        @DefaultValue("25") BigDecimal marginPercent,
        @DefaultValue("2") int minSources,
        @DefaultValue("15") BigDecimal marginFloorPercent,
        @DefaultValue("110") BigDecimal ceilingPercentOfMedian,
        @DefaultValue("5") BigDecimal maxDailyMovePercent,
        @DefaultValue("99") Integer roundingCents,
        @DefaultValue("48h") Duration freshness,
        @DefaultValue("true") boolean inStockOnly,
        @DefaultValue("false") boolean includeRefurbished,
        @DefaultValue("true") boolean excludeQuarantined,
        @DefaultValue("true") boolean excludeMarketplace) {

    public PricingStrategy toStrategy() {
        return switch (strategy.toLowerCase()) {
            case "index" -> new PricingStrategy.TargetIndex(indexPercent, switch (indexReference.toLowerCase()) {
                case "mean", "moyenne" -> PricingStrategy.TargetIndex.Reference.MEAN;
                case "min" -> PricingStrategy.TargetIndex.Reference.MIN;
                default -> PricingStrategy.TargetIndex.Reference.MEDIAN;
            });
            case "align" -> new PricingStrategy.AlignOnCheapest();
            case "undercut" -> new PricingStrategy.Undercut(undercutPercent);
            case "leader" -> new PricingStrategy.FollowLeader(leaderSource == null ? null : new SourceId(leaderSource), leaderOffsetPercent);
            case "cost-plus" -> new PricingStrategy.CostPlus(marginPercent);
            case "hold" -> new PricingStrategy.Hold();
            default -> throw new IllegalArgumentException("Unknown pricing strategy '" + strategy
                    + "' (expected index, align, undercut, leader, cost-plus or hold)");
        };
    }

    public PricingProfile toProfile() {
        return new PricingProfile(toStrategy(), minSources, marginFloorPercent, ceilingPercentOfMedian, maxDailyMovePercent, roundingCents);
    }

    public MarketRules toMarketRules() {
        MarketRules rules = MarketRules.defaults()
                .withFreshness(freshness)
                .withInStockOnly(inStockOnly)
                .withExcludeQuarantined(excludeQuarantined)
                .withExcludeMarketplace(excludeMarketplace);
        return includeRefurbished ? rules.includingRefurbished() : rules;
    }
}
