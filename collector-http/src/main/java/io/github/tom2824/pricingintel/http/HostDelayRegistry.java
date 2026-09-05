package io.github.tom2824.pricingintel.http;

import java.time.Duration;

/** Permet à un composant (ex. lecture de robots.txt) d'imposer un délai minimum plus long pour un hôte. */
public interface HostDelayRegistry {

    /** Relève l'intervalle minimum entre deux requêtes vers cet hôte ; sans effet si l'intervalle actuel est déjà plus long. */
    void raiseMinInterval(String host, Duration interval);
}
