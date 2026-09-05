/**
 * Adaptateur PostgreSQL (ADR 0018). Schéma versionné par Flyway dans {@code db/migration} ; catalogue en JPA
 * (familles, produits, identifiants, sources, annonces, correspondances) ; relevés, échecs et requêtes
 * d'analyse en SQL natif via {@code JdbcClient}.
 *
 * <p>Implémente les ports du cœur : {@code PriceSink} (relevés avec upsert quotidien et quarantaine),
 * {@code ListingProvider} (annonces actives et leur produit en vigueur), {@code CollectionReportSink}
 * (exécutions et échecs). L'importeur de catalogue charge un fichier YAML (produits, identifiants, annonces,
 * correspondances manuelles). Actif uniquement sous le profil Spring {@code postgres}.
 */
package io.github.tom2824.pricingintel.persistence;
