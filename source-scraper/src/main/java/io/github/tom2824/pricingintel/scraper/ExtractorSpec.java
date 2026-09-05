package io.github.tom2824.pricingintel.scraper;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Map;

/**
 * Description déclarative d'un extracteur, telle qu'écrite dans le YAML d'un site. Le champ {@code type} choisit
 * l'implémentation. Ajouter un site ne demande donc pas de code, seulement un fichier de configuration ;
 * un site vraiment tordu pourra toujours recevoir un extracteur codé à la main, qui implémentera {@link Extractor}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ExtractorSpec.JsonLd.class, name = "jsonld"),
        @JsonSubTypes.Type(value = ExtractorSpec.EmbeddedJson.class, name = "embedded-json"),
        @JsonSubTypes.Type(value = ExtractorSpec.Css.class, name = "css")
})
public sealed interface ExtractorSpec {

    Extractor build(String defaultCurrency);

    /** Données structurées schema.org. Aucun paramètre : le format est standard. */
    record JsonLd() implements ExtractorSpec {
        @Override
        public Extractor build(String defaultCurrency) {
            return new JsonLdExtractor(defaultCurrency);
        }
    }

    /**
     * @param script   sélecteur CSS d'un script contenant du JSON pur (ex. {@code script#__NEXT_DATA__})
     * @param variable nom de variable affectée dans un script (ex. {@code window.__INITIAL_STATE__})
     * @param paths    pointeurs JSON par champ ({@code price} obligatoire)
     */
    record EmbeddedJson(String script, String variable, Map<String, String> paths) implements ExtractorSpec {
        @Override
        public Extractor build(String defaultCurrency) {
            return new EmbeddedJsonExtractor(script, variable, paths, defaultCurrency);
        }
    }

    /**
     * Sélecteurs CSS, chacun pouvant se terminer par {@code @attribut}.
     *
     * @param inStock    mots-clés (sans casse) qui signifient "en stock" dans le texte de {@code availability}
     * @param outOfStock mots-clés qui signifient "rupture", testés en premier
     * @param currency   code ISO 4217 si le site n'affiche pas la devise du projet
     */
    record Css(
            String price,
            String listPrice,
            String availability,
            List<String> inStock,
            List<String> outOfStock,
            String gtin,
            String brand,
            String mpn,
            String sku,
            String title,
            String currency) implements ExtractorSpec {

        public static Css price(String priceSelector) {
            return new Css(priceSelector, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public Extractor build(String defaultCurrency) {
            return new CssExtractor(this, defaultCurrency);
        }
    }
}
