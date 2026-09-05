package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.ListingId;
import java.time.Instant;

/**
 * Port de sortie : archive la réponse brute d'une collecte. Permet de rejouer l'extraction après une refonte
 * du site, et de prouver ce que la page affichait quand un prix semble aberrant.
 * Une erreur d'archivage ne doit jamais faire échouer la collecte : l'appelant la journalise et continue.
 */
@FunctionalInterface
public interface RawSnapshotStore {

    void store(ListingId listingId, FetchResult result);

    /**
     * Supprime les archives plus anciennes que la rétention propre à l'implémentation.
     *
     * @return le nombre d'archives supprimées ; 0 par défaut (pas de rétention)
     */
    default int purgeExpired(Instant now) {
        return 0;
    }

    /** Implémentation nulle, pour désactiver l'archivage. */
    static RawSnapshotStore none() {
        return (listingId, result) -> {
        };
    }
}
