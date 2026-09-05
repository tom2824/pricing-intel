package io.github.tom2824.pricingintel.scraper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforme un prix tel qu'affiché en nombre. Gère les formats français et anglais :
 * {@code 1 299,99 €}, {@code 1299.99}, {@code €1,299.99}, {@code 1299€99}, {@code 1.299,00}, {@code 129,-}.
 * Règle : quand les deux séparateurs sont présents, le dernier est la décimale ; quand un seul est présent,
 * il est décimal s'il est suivi d'une ou deux décimales, sinon c'est un séparateur de milliers.
 */
public final class PriceParser {

    private static final Pattern EURO_BETWEEN_DIGITS = Pattern.compile("(\\d)\\s*[€$£]\\s*(\\d{1,2})(?!\\d)");
    private static final Pattern NUMBER = Pattern.compile("\\d(?:[\\d .,]*\\d)?");

    private PriceParser() {
    }

    public static Optional<BigDecimal> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ').strip();
        text = EURO_BETWEEN_DIGITS.matcher(text).replaceAll("$1,$2");

        Matcher matcher = NUMBER.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String number = matcher.group().replace(" ", "");

        int lastComma = number.lastIndexOf(',');
        int lastDot = number.lastIndexOf('.');
        char decimalSeparator;
        if (lastComma >= 0 && lastDot >= 0) {
            decimalSeparator = lastComma > lastDot ? ',' : '.';
        } else if (lastComma >= 0) {
            decimalSeparator = looksDecimal(number, ',') ? ',' : 0;
        } else if (lastDot >= 0) {
            decimalSeparator = looksDecimal(number, '.') ? '.' : 0;
        } else {
            decimalSeparator = 0;
        }

        StringBuilder normalized = new StringBuilder(number.length());
        for (char c : number.toCharArray()) {
            if (Character.isDigit(c)) {
                normalized.append(c);
            } else if (c == decimalSeparator) {
                normalized.append('.');
            }
        }
        try {
            return Optional.of(new BigDecimal(normalized.toString()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static boolean looksDecimal(String number, char separator) {
        int first = number.indexOf(separator);
        int last = number.lastIndexOf(separator);
        if (first != last) {
            return false;
        }
        int digitsAfter = number.length() - last - 1;
        return digitsAfter >= 1 && digitsAfter <= 2;
    }
}
