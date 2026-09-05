package io.github.tom2824.pricingintel.domain;

import java.util.Objects;

/** Identifiant typé d'une source de prix (un site, une API). */
public record SourceId(String value) {

    public SourceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SourceId cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
