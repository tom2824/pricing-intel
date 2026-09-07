package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Enchaîne la stratégie et les règles transverses dans l'ordre fixé par l'ADR 0022 :
 * sources minimum → stratégie → plancher de marge → plafond → variation maximale → arrondi.
 * Chaque étape écrit dans l'explication, qu'elle change le prix ou non.
 */
public final class PricingEngine {

    private static final PricingStrategy HOLD = new PricingStrategy.Hold();

    public Recommendation recommend(MarketView market, ProductContext product, PricingProfile profile) {
        Explanation explanation = new Explanation();
        explanation.note("marché", "marché " + scopeLabel(market.scope()) + " : " + market.summary());

        PricingStrategy strategy = profile.strategy();
        boolean fellBack = false;
        if (market.sourceCount() < profile.minSources() && !(strategy instanceof PricingStrategy.Hold)) {
            explanation.note("règle", "sources insuffisantes (" + market.sourceCount() + " < " + profile.minSources()
                    + ") : repli sur le maintien");
            strategy = HOLD;
            fellBack = true;
        }

        Optional<Money> candidate = strategy.propose(market, product, explanation);
        if (candidate.isEmpty() && !(strategy instanceof PricingStrategy.Hold)) {
            explanation.note("stratégie", "repli sur le maintien");
            strategy = HOLD;
            fellBack = true;
            candidate = strategy.propose(market, product, explanation);
        }
        if (candidate.isEmpty()) {
            explanation.note("résultat", "aucun prix proposable");
            return new Recommendation(product.id(), market.asOf(), market, strategy.id(), fellBack, null, explanation);
        }

        Money price = candidate.get();
        price = applyMarginFloor(price, product, profile, explanation);
        price = applyCeiling(price, market, profile, explanation);
        price = applyMaxDailyMove(price, product, profile, explanation);
        price = applyRounding(price, profile, explanation);

        return new Recommendation(product.id(), market.asOf(), market, strategy.id(), fellBack, price, explanation);
    }

    private static Money applyMarginFloor(Money price, ProductContext product, PricingProfile profile, Explanation explanation) {
        Optional<BigDecimal> margin = profile.marginFloor();
        Optional<Money> purchase = product.purchasePriceIfAny();
        if (margin.isEmpty() || purchase.isEmpty()) {
            return price;
        }
        Money floor = purchase.get().percent(margin.get());
        String label = "plancher marge " + margin.get().stripTrailingZeros().toPlainString() + " % (" + floor + ")";
        if (price.isLessThan(floor)) {
            explanation.add("règle", label + " appliqué", price, floor);
            return floor;
        }
        explanation.add("règle", label + " respecté", price, price);
        return price;
    }

    private static Money applyCeiling(Money price, MarketView market, PricingProfile profile, Explanation explanation) {
        Optional<BigDecimal> ceilingPercent = profile.ceiling();
        Optional<Money> median = market.median();
        if (ceilingPercent.isEmpty() || median.isEmpty()) {
            return price;
        }
        Money ceiling = median.get().times(ceilingPercent.get().movePointLeft(2));
        String label = "plafond " + ceilingPercent.get().stripTrailingZeros().toPlainString() + " % de la médiane (" + ceiling + ")";
        if (price.isGreaterThan(ceiling)) {
            explanation.add("règle", label + " appliqué", price, ceiling);
            return ceiling;
        }
        explanation.add("règle", label + " respecté", price, price);
        return price;
    }

    private static Money applyMaxDailyMove(Money price, ProductContext product, PricingProfile profile, Explanation explanation) {
        Optional<BigDecimal> maxMove = profile.maxDailyMove();
        Optional<Money> current = product.currentPriceIfAny();
        if (maxMove.isEmpty() || current.isEmpty()) {
            return price;
        }
        Money low = current.get().percent(maxMove.get().negate());
        Money high = current.get().percent(maxMove.get());
        BigDecimal move = price.ratioPercent(current.get()).subtract(BigDecimal.valueOf(100));
        String limit = "±" + maxMove.get().stripTrailingZeros().toPlainString() + " %";
        if (price.isLessThan(low)) {
            explanation.add("règle", "variation " + signed(move) + " % limitée à " + limit + " du prix actuel", price, low);
            return low;
        }
        if (price.isGreaterThan(high)) {
            explanation.add("règle", "variation " + signed(move) + " % limitée à " + limit + " du prix actuel", price, high);
            return high;
        }
        explanation.add("règle", "variation " + signed(move) + " % dans la limite de " + limit, price, price);
        return price;
    }

    private static Money applyRounding(Money price, PricingProfile profile, Explanation explanation) {
        Optional<Integer> cents = profile.rounding();
        if (cents.isEmpty()) {
            return price;
        }
        BigDecimal target = new BigDecimal(cents.get()).movePointLeft(2);
        BigDecimal units = price.amount().setScale(0, RoundingMode.FLOOR);
        BigDecimal rounded = units.add(target);
        if (rounded.compareTo(price.amount()) > 0) {
            rounded = units.subtract(BigDecimal.ONE).add(target);
        }
        Money result = Money.of(rounded, price.currency());
        explanation.add("règle", "arrondi ," + String.format("%02d", cents.get()) + " vers le bas", price, result);
        return result;
    }

    private static String signed(BigDecimal percent) {
        String value = percent.setScale(1, RoundingMode.HALF_UP).toPlainString();
        return percent.signum() > 0 ? "+" + value : value;
    }

    private static String scopeLabel(MarketScope scope) {
        return scope == MarketScope.STRICT ? "strict" : "de segment";
    }
}
