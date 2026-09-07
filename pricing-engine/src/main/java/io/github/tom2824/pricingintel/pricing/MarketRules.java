package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.ItemCondition;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Les règles qui délimitent le marché (ADR 0020). Toutes paramétrables ; les valeurs par défaut sont celles
 * de l'ADR : 48 h de fraîcheur, en stock seulement, neuf seulement, quarantaine et marketplace exclues.
 *
 * @param conditions états acceptés ; {@code UNKNOWN} est toujours toléré (une source qui ne dit rien vend du neuf)
 */
public record MarketRules(
        Duration freshness,
        boolean inStockOnly,
        Set<ItemCondition> conditions,
        boolean excludeQuarantined,
        boolean excludeMarketplace) {

    public MarketRules {
        Objects.requireNonNull(freshness, "freshness");
        Objects.requireNonNull(conditions, "conditions");
        if (freshness.isNegative() || freshness.isZero()) {
            throw new IllegalArgumentException("freshness must be positive");
        }
        conditions = Set.copyOf(conditions);
    }

    public static MarketRules defaults() {
        return new MarketRules(Duration.ofHours(48), true, Set.of(ItemCondition.NEW), true, true);
    }

    /** La vue alternative de l'ADR 0020 : neuf et reconditionné. */
    public MarketRules includingRefurbished() {
        return new MarketRules(freshness, inStockOnly, Set.of(ItemCondition.NEW, ItemCondition.REFURBISHED),
                excludeQuarantined, excludeMarketplace);
    }

    public MarketRules withFreshness(Duration value) {
        return new MarketRules(value, inStockOnly, conditions, excludeQuarantined, excludeMarketplace);
    }

    public MarketRules withInStockOnly(boolean value) {
        return new MarketRules(freshness, value, conditions, excludeQuarantined, excludeMarketplace);
    }

    public MarketRules withExcludeQuarantined(boolean value) {
        return new MarketRules(freshness, inStockOnly, conditions, value, excludeMarketplace);
    }

    public MarketRules withExcludeMarketplace(boolean value) {
        return new MarketRules(freshness, inStockOnly, conditions, excludeQuarantined, value);
    }
}
