/**
 * Implémentation du port {@link io.github.tom2824.pricingintel.collector.PageFetcher} avec le client HTTP du JDK,
 * habillée de décorateurs indépendants : limitation de débit par hôte, retry avec backoff, respect de robots.txt.
 * Le proxy est une stratégie ({@link io.github.tom2824.pricingintel.http.ProxyPolicy}) choisie en configuration.
 *
 * <p>Chaîne assemblée par {@link io.github.tom2824.pricingintel.http.Fetchers#polite} :
 * robots.txt → retry → rate limit → JDK. Chaque tentative du retry passe par le rate limit, et la lecture
 * de robots.txt passe par la même chaîne interne que les pages.
 */
package io.github.tom2824.pricingintel.http;
