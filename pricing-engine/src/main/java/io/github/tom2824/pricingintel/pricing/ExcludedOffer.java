package io.github.tom2824.pricingintel.pricing;

/** Une offre écartée du marché et la raison, pour que l'explication du prix soit complète (ADR 0020). */
public record ExcludedOffer(ObservedOffer offer, String reason) {
}
