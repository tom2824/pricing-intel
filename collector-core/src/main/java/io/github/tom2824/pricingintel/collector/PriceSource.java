package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.Listing;
import io.github.tom2824.pricingintel.domain.PriceSnapshot;

/**
 * Port d'entrée : une façon d'obtenir le prix d'une annonce (un scraper, un client d'API...).
 * Une source dit d'abord si elle sait traiter une annonce, puis l'observe.
 */
public interface PriceSource {

    /** Identifiant court, utilisé dans les rapports et les logs (ex. {@code scraper}, {@code cheapshark}). */
    String id();

    boolean supports(Listing listing);

    /**
     * Observe l'annonce et produit un relevé.
     *
     * @throws ObservationException si le prix n'a pas pu être obtenu ; {@link ObservationException#isRetryable()}
     *                              indique si une nouvelle tentative plus tard a un sens
     */
    PriceSnapshot observe(Listing listing) throws ObservationException;
}
