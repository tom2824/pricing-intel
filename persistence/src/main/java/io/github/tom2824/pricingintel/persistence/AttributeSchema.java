package io.github.tom2824.pricingintel.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le schéma de caractéristiques d'une famille, et la normalisation des valeurs d'un produit contre ce schéma :
 * « 12 Go », « 12GB » et « 12 GB » deviennent le nombre 12, une énumération prend sa forme canonique,
 * une caractéristique inconnue est refusée. Sans ça, ni la clé naturelle ni la clé de segment ne tiennent.
 */
public final class AttributeSchema {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build();
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");
    private static final Set<String> TRUE_WORDS = Set.of("true", "yes", "oui", "1", "y", "o");
    private static final Set<String> FALSE_WORDS = Set.of("false", "no", "non", "0", "n");

    private final List<FamilyAttribute> attributes;

    public AttributeSchema(List<FamilyAttribute> attributes) {
        this.attributes = attributes.stream()
                .sorted(Comparator.comparing(FamilyAttribute::code))
                .toList();
    }

    public static AttributeSchema fromJson(List<Map<String, Object>> raw) {
        List<FamilyAttribute> parsed = MAPPER.convertValue(raw == null ? List.of() : raw, new TypeReference<>() {
        });
        return new AttributeSchema(parsed);
    }

    public List<FamilyAttribute> attributes() {
        return attributes;
    }

    public List<FamilyAttribute> withRole(FamilyAttribute.Role role) {
        return attributes.stream().filter(a -> a.has(role)).toList();
    }

    /** @throws IllegalArgumentException caractéristique inconnue, valeur d'énumération hors liste, nombre illisible */
    public Map<String, Object> normalize(Map<String, Object> raw) {
        Map<String, Object> normalized = new TreeMap<>();
        if (raw == null) {
            return normalized;
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            FamilyAttribute attribute = attributes.stream()
                    .filter(a -> a.code().equals(entry.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown attribute '" + entry.getKey() + "'"));
            if (entry.getValue() == null) {
                continue;
            }
            normalized.put(attribute.code(), normalizeValue(attribute, entry.getValue()));
        }
        return normalized;
    }

    static Object normalizeValue(FamilyAttribute attribute, Object value) {
        return switch (attribute.type()) {
            case NUMBER -> toNumber(attribute, value);
            case BOOLEAN -> toBoolean(attribute, value);
            case ENUM -> toEnum(attribute, value);
            case TEXT -> String.valueOf(value).strip().replaceAll("\\s+", " ");
        };
    }

    private static BigDecimal toNumber(FamilyAttribute attribute, Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros();
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros();
        }
        Matcher matcher = NUMBER.matcher(String.valueOf(value).replace(' ', ' '));
        if (!matcher.find()) {
            throw new IllegalArgumentException("Attribute '" + attribute.code() + "' expects a number, got '" + value + "'");
        }
        return new BigDecimal(matcher.group().replace(',', '.')).stripTrailingZeros();
    }

    private static Boolean toBoolean(FamilyAttribute attribute, Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String word = String.valueOf(value).strip().toLowerCase(Locale.ROOT);
        if (TRUE_WORDS.contains(word)) {
            return true;
        }
        if (FALSE_WORDS.contains(word)) {
            return false;
        }
        throw new IllegalArgumentException("Attribute '" + attribute.code() + "' expects a boolean, got '" + value + "'");
    }

    private static String toEnum(FamilyAttribute attribute, Object value) {
        String wanted = Keys.normalize(String.valueOf(value));
        return attribute.values().stream()
                .filter(candidate -> Keys.normalize(candidate).equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Attribute '" + attribute.code() + "' must be one of "
                        + attribute.values() + ", got '" + value + "'"));
    }
}
