package io.github.tom2824.pricingintel.domain;

import java.util.Objects;

/**
 * Traçabilité d'un relevé : par quelle méthode le prix a été obtenu et avec quel niveau de confiance.
 * Une méthode structurée (JSON-LD) est plus fiable qu'un sélecteur CSS, et le moteur d'analyse
 * doit pouvoir en tenir compte.
 *
 * @param method     identifiant court de la méthode, ex. {@code jsonld}, {@code css}, {@code api:cheapshark}
 * @param confidence entre 0 et 1
 */
public record Extraction(String method, double confidence) {

    public Extraction {
        Objects.requireNonNull(method, "method");
        if (method.isBlank()) {
            throw new IllegalArgumentException("method cannot be blank");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be within [0, 1], got " + confidence);
        }
    }
}
