package io.github.tom2824.pricingintel.persistence;

/**
 * Normalisation d'un GTIN (EAN-13, UPC-12, EAN-8, GTIN-14) : chiffres seulement, UPC-12 complété à 13,
 * chiffre de contrôle vérifié. Une valeur mal extraite d'une page ne doit pas entrer dans le catalogue.
 */
public final class Gtin {

    private Gtin() {
    }

    /** @throws IllegalArgumentException longueur inattendue ou chiffre de contrôle faux */
    public static String normalize(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.length() == 12) {
            digits = "0" + digits;
        }
        if (digits.length() != 8 && digits.length() != 13 && digits.length() != 14) {
            throw new IllegalArgumentException("GTIN must have 8, 12, 13 or 14 digits, got '" + raw + "'");
        }
        if (!isValid(digits)) {
            throw new IllegalArgumentException("GTIN check digit is wrong for '" + raw + "'");
        }
        return digits;
    }

    static boolean isValid(String digits) {
        int sum = 0;
        int weight = 3;
        for (int i = digits.length() - 2; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 3 ? 1 : 3;
        }
        int check = (10 - sum % 10) % 10;
        return check == digits.charAt(digits.length() - 1) - '0';
    }
}
