package io.github.tom2824.pricingintel.domain;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Une annonce : un produit tel qu'il est vendu à un endroit précis (une URL sur un site, ou une
 * référence externe dans une API). C'est l'unité de collecte : chaque relevé de prix porte sur une annonce.
 *
 * @param externalRef identifiant côté source quand ce n'est pas une page HTML (ex. id CheapShark), sinon null
 */
public record Listing(ListingId id, ProductId productId, SourceId sourceId, URI url, String externalRef) {

    public Listing {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(url, "url");
    }

    public Listing(ListingId id, ProductId productId, SourceId sourceId, URI url) {
        this(id, productId, sourceId, url, null);
    }

    public Optional<String> externalReference() {
        return Optional.ofNullable(externalRef);
    }

    /** Hôte en minuscules, chaîne vide si l'URL n'en a pas. */
    public String host() {
        String host = url.getHost();
        return host == null ? "" : host.toLowerCase();
    }
}
