package io.github.tom2824.pricingintel.domain;

import java.util.Objects;

/** Identifiant typé : empêche de passer un id de produit là où un id d'annonce est attendu. */
public record ListingId(String value) {

    public ListingId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ListingId cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
