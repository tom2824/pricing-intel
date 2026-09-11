package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.Listing;
import io.github.tom2824.pricingintel.domain.ListingId;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Port d'entrée : la liste des annonces à relever lors d'une collecte (fichier YAML, base de données...). */
@FunctionalInterface
public interface ListingProvider {

    List<Listing> listings();

    /**
     * Les annonces qui ont déjà leur relevé du jour (ADR 0017 : un relevé par annonce et par jour) : la collecte
     * ne les refetche pas. Par défaut aucune, pour les fournisseurs sans mémoire (fichier YAML).
     */
    default Set<ListingId> collectedOn(LocalDate day) {
        return Set.of();
    }
}
