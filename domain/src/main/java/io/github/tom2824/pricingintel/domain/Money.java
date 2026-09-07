package io.github.tom2824.pricingintel.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Montant monétaire, toujours arrondi à deux décimales.
 * Comparer deux montants de devises différentes est une erreur de programmation, pas un cas métier.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money eur(String amount) {
        return of(amount, "EUR");
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    /** Multiplie par un facteur (ex. 0.98 pour un index de 98 %). */
    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    /** Applique un pourcentage : {@code percent(-2)} retire 2 %. */
    public Money percent(BigDecimal percentage) {
        return times(BigDecimal.ONE.add(percentage.movePointLeft(2)));
    }

    /** Rapport à un autre montant, en pourcentage à deux décimales : 98.00 signifie 98 % de {@code other}. */
    public BigDecimal ratioPercent(Money other) {
        requireSameCurrency(other);
        if (other.amount.signum() == 0) {
            throw new ArithmeticException("Cannot compute a ratio against zero");
        }
        return amount.multiply(BigDecimal.valueOf(100)).divide(other.amount, 2, RoundingMode.HALF_UP);
    }

    public Money min(Money other) {
        return isLessThan(other) ? this : other;
    }

    public Money max(Money other) {
        return isGreaterThan(other) ? this : other;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot combine " + currency.getCurrencyCode() + " with " + other.currency.getCurrencyCode());
        }
    }

    @Override
    public int compareTo(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot compare " + currency.getCurrencyCode() + " with " + other.currency.getCurrencyCode());
        }
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
