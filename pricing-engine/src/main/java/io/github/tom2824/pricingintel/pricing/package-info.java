/**
 * Moteur de prix (ADR 0020, 0022), Java pur : dépend du domaine et du JDK, de rien d'autre.
 *
 * <ol>
 *   <li>{@link io.github.tom2824.pricingintel.pricing.MarketBuilder} transforme des offres observées en
 *       {@link io.github.tom2824.pricingintel.pricing.MarketView} en appliquant les
 *       {@link io.github.tom2824.pricingintel.pricing.MarketRules} ; chaque offre écartée garde sa raison.</li>
 *   <li>Une {@link io.github.tom2824.pricingintel.pricing.PricingStrategy} propose un prix candidat.</li>
 *   <li>Les règles transverses de {@link io.github.tom2824.pricingintel.pricing.PricingEngine} l'encadrent,
 *       dans un ordre fixe.</li>
 *   <li>Le résultat est une {@link io.github.tom2824.pricingintel.pricing.Recommendation} avec son
 *       {@link io.github.tom2824.pricingintel.pricing.Explanation} étape par étape.</li>
 * </ol>
 */
package io.github.tom2824.pricingintel.pricing;
