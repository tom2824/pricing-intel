package io.github.tom2824.pricingintel.sink.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HtmlDistillerTest {

    private final HtmlDistiller distiller = new HtmlDistiller();

    static String fixture(String name) {
        try (InputStream in = HtmlDistillerTest.class.getResourceAsStream("/pages/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void keepsEmbeddedJsonBlocksIntactWithTheirOrigin() {
        DistilledPage page = distiller.distill(fixture("product-page.html"), "https://www.shop.test/p/1");

        assertThat(page.jsonBlocks()).extracting(DistilledPage.JsonBlock::source)
                .containsExactly("ld+json", "ld+json", "#__NEXT_DATA__", "window.__INITIAL_STATE__");
        assertThat(page.jsonBlocks().get(0).content().get("gtin13").asText()).isEqualTo("4711377114363");
        assertThat(page.jsonBlocks().get(1).content().asText()).isEqualTo("ceci n'est pas du json");
        assertThat(page.jsonBlocks().get(2).content().at("/props/pageProps/product/price").decimalValue())
                .isEqualByComparingTo("629.95");
        assertThat(page.jsonBlocks().get(3).content().at("/product/name").asText()).isEqualTo("Boîtier { test }");
    }

    @Test
    void rendersVisibleContentAsMarkdownWithoutChrome() {
        DistilledPage page = distiller.distill(fixture("product-page.html"), "https://www.shop.test/p/1");
        String md = page.markdown();

        assertThat(page.title()).startsWith("MSI GeForce RTX 4070 SUPER");
        assertThat(md).startsWith("# MSI GeForce RTX 4070 SUPER 12G VENTUS 2X OC");
        assertThat(md).contains("![Photo de la carte graphique]");
        assertThat(md).contains("Prix : 629,95 € 699,99 €");
        assertThat(md).contains("En stock, expédié sous 24h\nLivraison offerte");
        assertThat(md).contains("[Ajouter au panier]");
        assertThat(md).contains("## Caractéristiques");
        assertThat(md).contains("- **Chipset** : GeForce RTX 4070 SUPER\n- **Mémoire** : 12 Go GDDR6X");
        assertThat(md).contains("| Connectique | Nombre |\n| --- | --- |\n| HDMI 2.1 | 1 |\n| DisplayPort \\| 1.4a | 3 |");
        assertThat(md).contains("1. Premier point\n2. Deuxième point");

        assertThat(md).doesNotContain("Accueil", "Panier", "Composants", "Produits similaires", "Mentions légales",
                "Texte décoratif invisible", "color: red", "dataLayer");
        assertThat(md).doesNotContain("\n\n\n");
    }

    @Test
    void fallsBackToBodyAndFirstHeadingWhenThereIsNoMainOrTitle() {
        DistilledPage page = distiller.distill("<html><body><div><h1>Seul titre</h1><p>Texte</p></div></body></html>", "");

        assertThat(page.title()).isEqualTo("Seul titre");
        assertThat(page.markdown()).isEqualTo("# Seul titre\n\nTexte");
        assertThat(page.jsonBlocks()).isEmpty();
    }

    @Test
    void survivesEmptyInput() {
        DistilledPage page = distiller.distill(null, null);

        assertThat(page.title()).isEmpty();
        assertThat(page.markdown()).isEmpty();
    }

    @Test
    void balancedJsonHandlesStringsAndNesting() {
        assertThat(HtmlDistiller.balancedJson("= {\"a\":[1,{\"b\":\"}\"}]}; next", 1)).isEqualTo("{\"a\":[1,{\"b\":\"}\"}]}");
        assertThat(HtmlDistiller.balancedJson("= 42", 1)).isNull();
        assertThat(HtmlDistiller.balancedJson("= {\"open\": 1", 1)).isNull();
    }
}
