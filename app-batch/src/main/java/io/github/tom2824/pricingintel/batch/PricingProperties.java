package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.domain.SourceId;
import io.github.tom2824.pricingintel.pricing.MarketRules;
import io.github.tom2824.pricingintel.pricing.PricingProfile;
import io.github.tom2824.pricingintel.pricing.PricingStrategy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Les profils de prix précalculés (ADR 0022, ADR 0005) et les règles du marché (ADR 0020), sous le préfixe
 * {@code pricing}. Le premier profil de la liste est le profil de référence des vues de synthèse ; les autres
 * alimentent le simulateur du portfolio. Les garde-fous sont communs à tous les profils.
 *
 * @param profiles stratégies au format {@code index:98}, {@code index:100:mean}, {@code align}, {@code undercut:1},
 *                 {@code cost-plus:25}, {@code leader:ldlc:-2}, {@code hold}
 */
@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue({"index:98", "index:95", "index:100", "index:103", "align", "undercut:1", "cost-plus:25"}) List<String> profiles,
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

    /** Profils dans l'ordre déclaré, clé normalisée ({@code index:98} → {@code index-98}). */
    public Map<String, PricingProfile> toProfiles() {
        Map<String, PricingProfile> result = new LinkedHashMap<>();
        for (String spec : profiles) {
            String key = spec.strip().toLowerCase(Locale.ROOT).replace(':', '-');
            if (result.put(key, toProfile(parseStrategy(spec))) != null) {
                throw new IllegalArgumentException("Duplicate pricing profile '" + key + "'");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("pricing.profiles must declare at least one profile");
        }
        return result;
    }

    public String defaultProfileKey() {
        return toProfiles().keySet().iterator().next();
    }

    public PricingProfile toProfile(PricingStrategy strategy) {
        return new PricingProfile(strategy, minSources, marginFloorPercent, ceilingPercentOfMedian, maxDailyMovePercent, roundingCents);
    }

    static PricingStrategy parseStrategy(String spec) {
        String[] parts = spec.strip().split(":");
        String name = parts[0].toLowerCase(Locale.ROOT);
        return switch (name) {
            case "index" -> new PricingStrategy.TargetIndex(
                    parts.length > 1 ? new BigDecimal(parts[1]) : new BigDecimal("98"),
                    parts.length > 2 ? reference(parts[2]) : PricingStrategy.TargetIndex.Reference.MEDIAN);
            case "align" -> new PricingStrategy.AlignOnCheapest();
            case "undercut" -> new PricingStrategy.Undercut(parts.length > 1 ? new BigDecimal(parts[1]) : BigDecimal.ONE);
            case "cost-plus" -> new PricingStrategy.CostPlus(parts.length > 1 ? new BigDecimal(parts[1]) : new BigDecimal("25"));
            case "leader" -> new PricingStrategy.FollowLeader(parts.length > 1 ? new SourceId(parts[1]) : null,
                    parts.length > 2 ? new BigDecimal(parts[2]) : BigDecimal.ZERO);
            case "hold" -> new PricingStrategy.Hold();
            default -> throw new IllegalArgumentException("Unknown pricing strategy '" + spec
                    + "' (expected index, align, undercut, cost-plus, leader or hold)");
        };
    }

    private static PricingStrategy.TargetIndex.Reference reference(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "mean", "moyenne" -> PricingStrategy.TargetIndex.Reference.MEAN;
            case "min" -> PricingStrategy.TargetIndex.Reference.MIN;
            default -> PricingStrategy.TargetIndex.Reference.MEDIAN;
        };
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
