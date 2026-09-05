package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.Listing;
import java.util.List;

/** Port d'entrée : la liste des annonces à relever lors d'une collecte (fichier YAML, base de données...). */
@FunctionalInterface
public interface ListingProvider {

    List<Listing> listings();
}
