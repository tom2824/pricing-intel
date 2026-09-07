package io.github.tom2824.pricingintel.pricing;

import io.github.tom2824.pricingintel.domain.Money;
import io.github.tom2824.pricingintel.domain.ProductId;
import io.github.tom2824.pricingintel.domain.SourceId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * La photo du marché d'un produit à une date (ADR 0017, 0020) : les offres retenues, les offres écartées avec
 * leur raison, et les agrégats. C'est tout ce qu'une stratégie a le droit de regarder.
 */
public record MarketView(
        ProductId product,
        MarketScope scope,
        Instant asOf,
        Currency currency,
        List<ObservedOffer> retained,
        List<ExcludedOffer> excluded) {

    public MarketView {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(currency, "currency");
        retained = retained.stream().sorted(Comparator.comparing(ObservedOffer::price)).toList();
        excluded = List.copyOf(excluded);
        for (ObservedOffer offer : retained) {
            if (!offer.price().currency().equals(currency)) {
                throw new IllegalArgumentException("Offer " + offer.listing() + " is not in " + currency);
            }
        }
    }

    public boolean isEmpty() {
        return retained.isEmpty();
    }

    /** Nombre d'enseignes distinctes parmi les offres retenues. */
    public int sourceCount() {
        return (int) retained.stream().map(ObservedOffer::source).distinct().count();
    }

    public Optional<ObservedOffer> cheapest() {
        return retained.stream().findFirst();
    }

    public Optional<Money> min() {
        return cheapest().map(ObservedOffer::price);
    }

    public Optional<Money> max() {
        return retained.isEmpty() ? Optional.empty() : Optional.of(retained.get(retained.size() - 1).price());
    }

    public Optional<Money> mean() {
        if (retained.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = retained.stream().map(o -> o.price().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(Money.of(sum.divide(BigDecimal.valueOf(retained.size()), 4, RoundingMode.HALF_UP), currency));
    }

    /** Médiane des prix retenus ; moyenne des deux valeurs centrales pour un nombre pair d'offres. */
    public Optional<Money> median() {
        if (retained.isEmpty()) {
            return Optional.empty();
        }
        int n = retained.size();
        if (n % 2 == 1) {
            return Optional.of(retained.get(n / 2).price());
        }
        BigDecimal middle = retained.get(n / 2 - 1).price().amount().add(retained.get(n / 2).price().amount());
        return Optional.of(Money.of(middle.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP), currency));
    }

    /** L'offre la moins chère d'une enseigne donnée parmi les offres retenues. */
    public Optional<ObservedOffer> offerOf(SourceId source) {
        return retained.stream().filter(o -> o.source().equals(source)).findFirst();
    }

    /** Résumé lisible : « 3 offres de 3 enseignes, min 959,99 EUR, médiane 979,95 EUR, max 979,95 EUR ». */
    public String summary() {
        if (retained.isEmpty()) {
            return "aucune offre retenue (" + excluded.size() + " écartée(s))";
        }
        return "%d offre(s) de %d enseigne(s), min %s, médiane %s, max %s, %d écartée(s)".formatted(
                retained.size(), sourceCount(), min().orElseThrow(), median().orElseThrow(), max().orElseThrow(), excluded.size());
    }
}
