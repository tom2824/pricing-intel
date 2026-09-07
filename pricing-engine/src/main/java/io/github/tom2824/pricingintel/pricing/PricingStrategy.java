package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.SourceId;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Une stratégie répond à « où je veux être par rapport au marché » et propose un prix candidat (ADR 0022).
 * Elle ne connaît ni plancher de marge ni arrondi : c'est le rôle des règles transverses du moteur.
 * Une stratégie qui ne peut pas s'appliquer rend vide en expliquant pourquoi ; le moteur se replie sur Maintien.
 */
public sealed interface PricingStrategy {

    String id();

    /** Libellé humain, avec les paramètres, pour l'explication. */
    String describe();

    Optional<Money> propose(MarketView market, ProductContext product, Explanation explanation);

    // ---------------------------------------------------------------------------------------------------

    /** Le prix le moins cher du marché. */
    record AlignOnCheapest() implements PricingStrategy {
        @Override
        public String id() {
            return "align";
        }

        @Override
        public String describe() {
            return "alignement sur le moins cher";
        }

        @Override
        public Optional<Money> propose(MarketView market, ProductContext product, Explanation explanation) {
            Optional<ObservedOffer> cheapest = market.cheapest();
            cheapest.ifPresent(o -> explanation.add("stratégie",
                    "alignement sur le moins cher (" + o.source() + ")", null, o.price()));
            return cheapest.map(ObservedOffer::price);
        }
    }

    /** Le moins cher, moins un pourcentage. */
    record Undercut(BigDecimal percent) implements PricingStrategy {
        public Undercut {
            Objects.requireNonNull(percent, "percent");
            if (percent.signum() <= 0) {
                throw new IllegalArgumentException("undercut percent must be positive");
            }
        }

        @Override
        public String id() {
            return "undercut";
        }

        @Override
        public String describe() {
            return "undercut de " + percent.stripTrailingZeros().toPlainString() + " % sous le moins cher";
        }

        @Override
        public Optional<Money> propose(MarketView market, ProductContext product, Explanation explanation) {
            return market.cheapest().map(o -> {
                Money candidate = o.price().percent(percent.negate());
                explanation.add("stratégie", describe() + " (" + o.source() + ")", o.price(), candidate);
                return candidate;
            });
        }
    }

    /** Un pourcentage d'une référence du marché : 98 % de la médiane, 100 % de la moyenne, 103 % du minimum. */
    record TargetIndex(BigDecimal indexPercent, Reference reference) implements PricingStrategy {
        public enum Reference { MEDIAN, MEAN, MIN }

        public TargetIndex {
            Objects.requireNonNull(indexPercent, "indexPercent");
            reference = reference == null ? Reference.MEDIAN : reference;
            if (indexPercent.signum() <= 0) {
                throw new IllegalArgumentException("index must be positive");
            }
        }

        public static TargetIndex ofMedian(String percent) {
            return new TargetIndex(new BigDecimal(percent), Reference.MEDIAN);
        }

        @Override
        public String id() {
            return "index";
        }

        @Override
        public String describe() {
            return "index " + indexPercent.stripTrailingZeros().toPlainString() + " % de la " + referenceLabel();
        }

        private String referenceLabel() {
            return switch (reference) {
                case MEDIAN -> "médiane";
                case MEAN -> "moyenne";
                case MIN -> "offre la moins chère";
            };
        }

        @Override
        public Optional<Money> propose(MarketView market, ProductContext product, Explanation explanation) {
            Optional<Money> base = switch (reference) {
                case MEDIAN -> market.median();
                case MEAN -> market.mean();
                case MIN -> market.min();
            };
            return base.map(reference -> {
                Money candidate = reference.times(indexPercent.movePointLeft(2));
                explanation.add("stratégie", describe(), reference, candidate);
                return candidate;
            });
        }
    }

    /** Le prix d'une enseigne désignée, plus ou moins un écart en pourcentage (négatif = en dessous). */
    record FollowLeader(SourceId leader, BigDecimal offsetPercent) implements PricingStrategy {
        public FollowLeader {
            offsetPercent = offsetPercent == null ? BigDecimal.ZERO : offsetPercent;
        }

        @Override
        public String id() {
            return "leader";
        }

        @Override
        public String describe() {
            String offset = offsetPercent.signum() == 0 ? "" : " " + (offsetPercent.signum() > 0 ? "+" : "")
                    + offsetPercent.stripTrailingZeros().toPlainString() + " %";
            return "suivi du leader" + (leader == null ? "" : " " + leader) + offset;
        }

        @Override
        public Optional<Money> propose(MarketView market, ProductContext product, Explanation explanation) {
            SourceId target = leader != null ? leader : product.leaderIfAny().orElse(null);
            if (target == null) {
                explanation.note("stratégie", "suivi du leader impossible : aucun leader désigné");
                return Optional.empty();
            }
            Optional<ObservedOffer> offer = market.offerOf(target);
            if (offer.isEmpty()) {
                explanation.note("stratégie", "suivi du leader impossible : " + target + " absent du marché retenu");
                return Optional.empty();
            }
            Money candidate = offer.get().price().percent(offsetPercent);
            explanation.add("stratégie", "suivi du leader " + target + (offsetPercent.signum() == 0 ? "" : " "
                    + (offsetPercent.signum() > 0 ? "+" : "") + offsetPercent.stripTrailingZeros().toPlainString() + " %"),
                    offer.get().price(), candidate);
            return Optional.of(candidate);
        }
    }

    /** Prix d'achat majoré d'une marge : indépendant du marché. */
    record CostPlus(BigDecimal marginPercent) implements PricingStrategy {
        public CostPlus {
            Objects.requireNonNull(marginPercent, "marginPercent");
            if (marginPercent.signum() < 0) {
                throw new IllegalArgumentException("margin cannot be negative");
            }
        }

        @Override
        public String id() {
            return "cost-plus";
        }

        @Override
        public String describe() {
            return "marge cible " + marginPercent.stripTrailingZeros().toPlainString() + " % sur le prix d'achat";
        }

        @Override
        public Optional<Money> propose(MarketView market, ProductContext product, Explanation explanation) {
            Optional<Money> purchase = product.purchasePriceIfAny();
            if (purchase.isEmpty()) {
                explanation.note("stratégie", "marge cible impossible : prix d'achat inconnu");
                return Optional.empty();
            }
            Money candidate = purchase.get().percent(marginPercent);
            explanation.add("stratégie", describe(), purchase.get(), candidate);
            return Optional.of(candidate);
        }
    }

    /** Notre prix actuel. Stratégie de repli quand le marché est absent ou qu'une autre stratégie ne s'applique pas. */
    record Hold() implements PricingStrategy {
        @Override
        public String id() {
            return "hold";
        }

        @Override
        public String describe() {
            return "maintien du prix actuel";
        }

        @Override
        public Optional<Money> propose(MarketView market, ProductContext product, Explanation explanation) {
            Optional<Money> current = product.currentPriceIfAny();
            if (current.isEmpty()) {
                explanation.note("stratégie", "maintien impossible : pas de prix actuel");
                return Optional.empty();
            }
            explanation.add("stratégie", "maintien du prix actuel", null, current.get());
            return current;
        }
    }
}
