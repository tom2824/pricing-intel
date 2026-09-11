package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.PriceSnapshot;

/**
 * Port de sortie : reçoit les relevés au fil de la collecte. Un fichier, une base, un webhook, la console.
 * Un sink qui échoue le signale par une exception : le relevé est consigné comme échec transitoire, et la
 * collecte s'arrête si le sink échoue plusieurs fois de suite (voir {@link CollectionRun}).
 */
public interface PriceSink extends AutoCloseable {

    void accept(PriceSnapshot snapshot);

    /** Libère les ressources (flush, fermeture de fichier ou de connexion). Sans effet par défaut. */
    @Override
    default void close() {
    }
}
