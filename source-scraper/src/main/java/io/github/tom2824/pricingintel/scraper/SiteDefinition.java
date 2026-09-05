package io.github.tom2824.pricingintel.scraper;

import java.util.List;
import java.util.Locale;

/**
 * Un site scrapable : un identifiant, un motif d'hôte et la chaîne d'extracteurs à essayer dans l'ordre.
 * Motifs d'hôte : {@code www.ldlc.com} (exact), {@code *.ldlc.com} (le domaine et ses sous-domaines), {@code *} (tout).
 */
public record SiteDefinition(String id, String host, List<ExtractorSpec> extractors) {

    public SiteDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("site id is required");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("site '" + id + "' needs a host pattern");
        }
        if (extractors == null || extractors.isEmpty()) {
            throw new IllegalArgumentException("site '" + id + "' needs at least one extractor");
        }
        host = host.toLowerCase(Locale.ROOT);
        extractors = List.copyOf(extractors);
    }

    /** Site générique : JSON-LD seulement, sur n'importe quel hôte. Sert de repli quand aucun site n'est déclaré. */
    public static SiteDefinition genericJsonLd() {
        return new SiteDefinition("generic-jsonld", "*", List.of(new ExtractorSpec.JsonLd()));
    }

    public boolean matches(String candidateHost) {
        String candidate = candidateHost.toLowerCase(Locale.ROOT);
        if (host.equals("*")) {
            return true;
        }
        if (host.startsWith("*.")) {
            String bare = host.substring(2);
            return candidate.equals(bare) || candidate.endsWith("." + bare);
        }
        return candidate.equals(host);
    }

    /** Plus le motif est précis, plus il est prioritaire : exact (2) > sous-domaines (1) > tout (0). */
    int specificity() {
        if (host.equals("*")) {
            return 0;
        }
        return host.startsWith("*.") ? 1 : 2;
    }
}
