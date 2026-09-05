package io.github.tom2824.pricingintel.persistence;

import java.util.List;
import java.util.Set;

/**
 * Une caractéristique déclarée par une famille (ADR 0015) : son type, son unité éventuelle, ses valeurs
 * autorisées pour une énumération, et ses rôles.
 */
public record FamilyAttribute(String code, String label, Type type, String unit, List<String> values, Set<Role> roles) {

    public enum Type { TEXT, NUMBER, ENUM, BOOLEAN }

    /** identity : clé naturelle ; equivalence : clé de segment ; descriptive : information. */
    public enum Role { IDENTITY, EQUIVALENCE, DESCRIPTIVE }

    public FamilyAttribute {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("attribute code is required");
        }
        type = type == null ? Type.TEXT : type;
        values = values == null ? List.of() : List.copyOf(values);
        roles = roles == null || roles.isEmpty() ? Set.of(Role.DESCRIPTIVE) : Set.copyOf(roles);
        if (type == Type.ENUM && values.isEmpty()) {
            throw new IllegalArgumentException("enum attribute '" + code + "' needs values");
        }
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }
}
