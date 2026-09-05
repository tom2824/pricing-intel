/**
 * Cœur de la collecte : les ports (interfaces) que les adaptateurs implémentent, et l'orchestration
 * d'une collecte.
 *
 * <p>Ports d'entrée : {@link io.github.tom2824.pricingintel.collector.PriceSource} (d'où viennent les prix),
 * {@link io.github.tom2824.pricingintel.collector.ListingProvider} (quoi relever).
 * Ports de sortie : {@link io.github.tom2824.pricingintel.collector.PriceSink} (où envoyer les relevés),
 * {@link io.github.tom2824.pricingintel.collector.RawSnapshotStore} (où archiver les pages brutes).
 * Port technique : {@link io.github.tom2824.pricingintel.collector.PageFetcher} (comment récupérer une URL).
 *
 * <p>Ce module ne connaît ni Spring, ni HTTP, ni aucun site. Il ne dépend que du domaine.
 */
package io.github.tom2824.pricingintel.collector;
