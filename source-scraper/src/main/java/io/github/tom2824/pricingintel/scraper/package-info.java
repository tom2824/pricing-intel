/**
 * Source de prix par lecture de pages HTML.
 *
 * <p>Un site est décrit en YAML ({@link io.github.tom2824.pricingintel.scraper.SiteDefinition}) : un hôte et une
 * chaîne d'extracteurs essayés dans l'ordre. Le premier qui trouve un prix gagne, et le relevé garde la trace
 * de la méthode et de sa confiance :
 * <ol>
 *   <li>{@code jsonld} : données structurées schema.org, présentes sur la plupart des e-commerçants (confiance 0.95)</li>
 *   <li>{@code embedded-json} : état applicatif embarqué ({@code __NEXT_DATA__}, {@code window.__INITIAL_STATE__}...) (0.85)</li>
 *   <li>{@code css} : sélecteurs CSS déclarés, fragiles aux refontes (0.7)</li>
 * </ol>
 *
 * <p>Ce module ne fait pas de HTTP lui-même : il passe par le port {@code PageFetcher}. Il ne connaît donc ni
 * les proxys, ni le rate limiting, ni robots.txt, et se teste entièrement hors ligne sur des pages fixtures.
 */
package io.github.tom2824.pricingintel.scraper;
