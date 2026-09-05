package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.PriceSnapshot;

/**
 * Port de sortie : reçoit les relevés au fil de la collecte. Un fichier, une base, un webhook, la console.
 * Un sink qui échoue interrompt la collecte : perdre silencieusement des relevés serait pire.
 */
public interface PriceSink extends AutoCloseable {

    void accept(PriceSnapshot snapshot);

    /** Libère les ressources (flush, fermeture de fichier ou de connexion). Sans effet par défaut. */
    @Override
    default void close() {
    }
}
