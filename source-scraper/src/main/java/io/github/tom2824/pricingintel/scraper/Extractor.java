package io.github.tom2824.pricingintel.scraper;

import java.util.Optional;
import org.jsoup.nodes.Document;

/** Une façon de lire une offre dans une page déjà téléchargée et parsée. Sans effet de bord, sans réseau. */
public interface Extractor {

    /** Nom court de la méthode, repris dans {@code PriceSnapshot.extraction().method()}. */
    String method();

    /** @return l'offre trouvée, ou vide si cette méthode ne s'applique pas à cette page */
    Optional<ExtractedOffer> extract(Document document);
}
