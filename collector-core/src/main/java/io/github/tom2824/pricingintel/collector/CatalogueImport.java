package io.github.tom2824.pricingintel.collector;

import java.nio.file.Path;

/**
 * Port d'entrée : charger un catalogue (produits, identifiants, annonces) dans le référentiel avant de collecter.
 * Idempotent par contrat : relancer l'import met à jour au lieu de dupliquer.
 */
public interface CatalogueImport {

    record Result(int productsCreated, int productsUpdated, int listingsCreated, int listingsUpdated, int matchesCreated) {
        public String summary() {
            return "%d produit(s) créé(s), %d mis à jour ; %d annonce(s) créée(s), %d mise(s) à jour ; %d correspondance(s) créée(s)"
                    .formatted(productsCreated, productsUpdated, listingsCreated, listingsUpdated, matchesCreated);
        }
    }

    Result importFile(Path file);
}
