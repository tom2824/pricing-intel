package io.github.tom2824.pricingintel.persistence;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Clés calculées d'un produit (ADR 0015). La clé naturelle porte l'identité (unique en base) ; la clé
 * d'équivalence définit le segment (ADR 0009). Toutes deux reposent sur une normalisation agressive
 * (minuscules, sans accents, sans ponctuation) pour que « RTX 4070 SUPER » et « rtx-4070-super » coïncident.
 */
public final class Keys {

    private Keys() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** famille | marque | référence (ou nom à défaut) | caractéristiques identifiantes, par code trié. */
    public static String naturalKey(String family, String brand, String mpn, String name,
                                    Map<String, Object> attributes, AttributeSchema schema) {
        StringBuilder key = new StringBuilder(family)
                .append('|').append(normalize(brand))
                .append('|').append(normalize(mpn == null || mpn.isBlank() ? name : mpn));
        for (FamilyAttribute attribute : schema.withRole(FamilyAttribute.Role.IDENTITY)) {
            key.append('|').append(attribute.code()).append('=').append(valueKey(attributes.get(attribute.code())));
        }
        return key.toString();
    }

    /** famille | caractéristiques d'équivalence ; null si la famille n'en déclare pas ou si une valeur manque. */
    public static String equivalenceKey(String family, Map<String, Object> attributes, AttributeSchema schema) {
        var equivalence = schema.withRole(FamilyAttribute.Role.EQUIVALENCE);
        if (equivalence.isEmpty()) {
            return null;
        }
        StringBuilder key = new StringBuilder(family);
        for (FamilyAttribute attribute : equivalence) {
            Object value = attributes.get(attribute.code());
            if (value == null) {
                return null;
            }
            key.append('|').append(attribute.code()).append('=').append(valueKey(value));
        }
        return key.toString();
    }

    static String valueKey(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        return normalize(String.valueOf(value));
    }
}
