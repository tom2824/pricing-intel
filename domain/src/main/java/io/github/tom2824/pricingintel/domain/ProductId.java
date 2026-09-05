package io.github.tom2824.pricingintel.domain;

import java.util.Objects;

/** Identifiant typé d'un produit du catalogue interne. */
public record ProductId(String value) {

    public ProductId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProductId cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
