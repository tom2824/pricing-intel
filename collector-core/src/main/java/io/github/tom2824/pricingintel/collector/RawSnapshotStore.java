package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.ListingId;

/**
 * Port de sortie : archive la réponse brute d'une collecte. Permet de rejouer l'extraction après une refonte
 * du site, et de prouver ce que la page affichait quand un prix semble aberrant.
 * Une erreur d'archivage ne doit jamais faire échouer la collecte : les implémentations la journalisent.
 */
@FunctionalInterface
public interface RawSnapshotStore {

    void store(ListingId listingId, FetchResult result);

    /** Implémentation nulle, pour désactiver l'archivage. */
    static RawSnapshotStore none() {
        return (listingId, result) -> {
        };
    }
}
