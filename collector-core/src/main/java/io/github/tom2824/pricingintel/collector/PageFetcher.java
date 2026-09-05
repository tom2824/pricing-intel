package io.github.tom2824.pricingintel.collector;

import java.net.URI;

/**
 * Port technique : récupère le contenu d'une URL. Les sources qui lisent des pages ou des API passent par lui,
 * ce qui permet d'empiler proxy, limitation de débit, retry et robots.txt sans que la source le sache.
 */
@FunctionalInterface
public interface PageFetcher {

    /**
     * @return la réponse, y compris en cas de statut HTTP d'erreur (4xx/5xx) : c'est à l'appelant de décider
     * @throws FetchException si aucune réponse n'a pu être obtenue (réseau, délai, interdiction robots.txt)
     */
    FetchResult fetch(URI uri) throws FetchException;
}
