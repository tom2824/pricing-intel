package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.domain.SourceId;
import io.github.tom2824.pricingintel.pricing.MarketRules;
import io.github.tom2824.pricingintel.pricing.PricingProfile;
import io.github.tom2824.pricingintel.pricing.PricingStrategy;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Les profils de prix précalculés (ADR 0022, ADR 0005) et les règles du marché (ADR 0020), sous le préfixe
 * {@code pricing}. Le premier profil de la liste est le profil de référence des vues de synthèse ; les autres
 * alimentent le simulateur du portfolio. Les garde-fous sont communs à tous les profils.
 *
 * <p>Tout est validé au démarrage, profils compris : une stratégie mal orthographiée ou un arrondi impossible
 * arrête l'application avant la collecte, pas une heure de scraping plus tard.
 *
 * @param profiles stratégies au format {@code index:98}, {@code index:100:mean}, {@code align}, {@code undercut:1},
 *                 {@code cost-plus:25}, {@code leader:ldlc:-2}, {@code hold}
 * @param roundingCents centimes du prix arrondi (99 pour un prix en ,99), entre 0 et 99
 */
@Validated
@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(
        @DefaultValue("true") boolean enabled,
        /** ADR 0023 : chaque jour, une règle tirée au sort met à jour notre prix. */
        @DefaultValue("true") boolean dailyDecision,
        @NotEmpty @DefaultValue({"index:98", "index:95", "index:100", "index:103", "align", "undercut:1", "cost-plus:25"}) List<String> profiles,
        @Min(1) @DefaultValue("2") int minSources,
        @NotNull @DecimalMin("0") @DefaultValue("15") BigDecimal marginFloorPercent,
        @NotNull @DecimalMin("0") @DefaultValue("110") BigDecimal ceilingPercentOfMedian,
        @NotNull @DecimalMin("0") @DefaultValue("5") BigDecimal maxDailyMovePercent,
        @NotNull @Min(0) @Max(99) @DefaultValue("99") Integer roundingCents,
        @NotNull @DefaultValue("48h") Duration freshness,
        @DefaultValue("true") boolean inStockOnly,
        @DefaultValue("false") boolean includeRefurbished,
        @DefaultValue("true") boolean excludeQuarantined,
        @DefaultValue("true") boolean excludeMarketplace) {

    public PricingProperties {
        if (profiles != null) {
            parseProfiles(profiles); // valide la syntaxe et l'unicité dès la liaison de la configuration
        }
        if (freshness != null && (freshness.isZero() || freshness.isNegative())) {
            throw new IllegalArgumentException("pricing.freshness must be positive, got " + freshness);
        }
    }

    /** Profils dans l'ordre déclaré, clé normalisée ({@code index:98} → {@code index-98}). */
    public Map<String, PricingProfile> toProfiles() {
        Map<String, PricingProfile> result = new LinkedHashMap<>();
        parseProfiles(profiles).forEach((key, strategy) -> result.put(key, toProfile(strategy)));
        return result;
    }

    public String defaultProfileKey() {
        return parseProfiles(profiles).keySet().iterator().next();
    }

    public PricingProfile toProfile(PricingStrategy strategy) {
        return new PricingProfile(strategy, minSources, marginFloorPercent, ceilingPercentOfMedian, maxDailyMovePercent, roundingCents);
    }

    /** Clés normalisées et stratégies, dans l'ordre déclaré ; refuse les doublons et les listes vides. */
    static Map<String, PricingStrategy> parseProfiles(List<String> specs) {
        Map<String, PricingStrategy> result = new LinkedHashMap<>();
        for (String spec : specs) {
            String key = spec.strip().toLowerCase(Locale.ROOT).replace(':', '-');
            if (result.put(key, parseStrategy(spec)) != null) {
                throw new IllegalArgumentException("Duplicate pricing profile '" + key + "'");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("pricing.profiles must declare at least one profile");
        }
        return result;
    }

    static PricingStrategy parseStrategy(String spec) {
        String[] parts = spec.strip().split(":");
        String name = parts[0].toLowerCase(Locale.ROOT);
        try {
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
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in pricing profile '" + spec + "'", e);
        }
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
