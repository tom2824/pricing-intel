/**
 * Sorties sur le système de fichiers.
 *
 * <ul>
 *   <li>{@link io.github.tom2824.pricingintel.sink.file.JsonLinesPriceSink} : une ligne JSON par relevé, facile à
 *       rejouer, à diff-er et à charger n'importe où.</li>
 *   <li>{@link io.github.tom2824.pricingintel.sink.file.DistilledSnapshotStore} : archive de long terme d'une page,
 *       réduite à ses blocs JSON et à son contenu visible en Markdown (ADR 0014).</li>
 *   <li>{@link io.github.tom2824.pricingintel.sink.file.FileRawSnapshotStore} : HTML complet gzippé, à courte
 *       rétention, pour déboguer un extracteur.</li>
 * </ul>
 * Les archives portent leur horodatage dans leur nom ; la purge s'appuie dessus.
 */
package io.github.tom2824.pricingintel.sink.file;
