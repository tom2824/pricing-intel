package io.github.tom2824.pricingintel.pricing;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Une stratégie et ses garde-fous (ADR 0022). Un profil par défaut ; plus tard un profil par famille ou produit.
 *
 * @param minSources             en dessous, le marché n'est pas fiable : repli sur Maintien
 * @param marginFloorPercent     jamais sous prix d'achat × (1 + marge), ou null pour désactiver
 * @param ceilingPercentOfMedian jamais au-dessus de X % de la médiane, ou null
 * @param maxDailyMovePercent    ± X % par rapport au prix actuel, ou null
 * @param roundingCents          terminaison psychologique (99, 95, 90), ou null pour ne pas arrondir
 */
public record PricingProfile(
        PricingStrategy strategy,
        int minSources,
        BigDecimal marginFloorPercent,
        BigDecimal ceilingPercentOfMedian,
        BigDecimal maxDailyMovePercent,
        Integer roundingCents) {

    public PricingProfile {
        Objects.requireNonNull(strategy, "strategy");
        if (minSources < 0) {
            throw new IllegalArgumentException("minSources cannot be negative");
        }
        if (roundingCents != null && (roundingCents < 0 || roundingCents > 99)) {
            throw new IllegalArgumentException("roundingCents must be within 0..99");
        }
    }

    /** Index 98 % de la médiane, 2 sources minimum, marge plancher 15 %, plafond 110 % de la médiane, ± 5 %/jour, arrondi ,99. */
    public static PricingProfile defaults() {
        return new PricingProfile(PricingStrategy.TargetIndex.ofMedian("98"), 2,
                new BigDecimal("15"), new BigDecimal("110"), new BigDecimal("5"), 99);
    }

    public PricingProfile withStrategy(PricingStrategy value) {
        return new PricingProfile(value, minSources, marginFloorPercent, ceilingPercentOfMedian, maxDailyMovePercent, roundingCents);
    }

    public PricingProfile withMaxDailyMovePercent(BigDecimal value) {
        return new PricingProfile(strategy, minSources, marginFloorPercent, ceilingPercentOfMedian, value, roundingCents);
    }

    public PricingProfile withMarginFloorPercent(BigDecimal value) {
        return new PricingProfile(strategy, minSources, value, ceilingPercentOfMedian, maxDailyMovePercent, roundingCents);
    }

    public PricingProfile withCeilingPercentOfMedian(BigDecimal value) {
        return new PricingProfile(strategy, minSources, marginFloorPercent, value, maxDailyMovePercent, roundingCents);
    }

    public PricingProfile withRoundingCents(Integer value) {
        return new PricingProfile(strategy, minSources, marginFloorPercent, ceilingPercentOfMedian, maxDailyMovePercent, value);
    }

    public PricingProfile withMinSources(int value) {
        return new PricingProfile(strategy, value, marginFloorPercent, ceilingPercentOfMedian, maxDailyMovePercent, roundingCents);
    }

    public Optional<BigDecimal> marginFloor() {
        return Optional.ofNullable(marginFloorPercent);
    }

    public Optional<BigDecimal> ceiling() {
        return Optional.ofNullable(ceilingPercentOfMedian);
    }

    public Optional<BigDecimal> maxDailyMove() {
        return Optional.ofNullable(maxDailyMovePercent);
    }

    public Optional<Integer> rounding() {
        return Optional.ofNullable(roundingCents);
    }
}
