package io.github.tom2824.pricingintel.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiteDefinitionLoaderTest {

    private final SiteDefinitionLoader loader = new SiteDefinitionLoader();

    @Test
    void parsesAFullSiteDefinition() {
        SiteDefinition site = loader.parse(Fixtures.read("sites/shop-test.yml"));

        assertThat(site.id()).isEqualTo("shop-test");
        assertThat(site.host()).isEqualTo("*.shop.test");
        assertThat(site.extractors()).hasSize(3);
        assertThat(site.extractors().get(0)).isInstanceOf(ExtractorSpec.JsonLd.class);
        assertThat(site.extractors().get(1)).isInstanceOfSatisfying(ExtractorSpec.EmbeddedJson.class, e -> {
            assertThat(e.script()).isEqualTo("script#__NEXT_DATA__");
            assertThat(e.paths()).containsEntry("price", "/props/pageProps/product/price");
        });
        assertThat(site.extractors().get(2)).isInstanceOfSatisfying(ExtractorSpec.Css.class, c -> {
            assertThat(c.price()).isEqualTo(".pricing .price");
            assertThat(c.availability()).isEqualTo(".stock");
        });
    }

    @Test
    void rejectsUnknownPropertiesSoTyposAreCaughtEarly() {
        assertThatThrownBy(() -> loader.parse(Fixtures.read("sites/bad-typo.yml.disabled")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prise");
    }

    @Test
    void rejectsUnknownExtractorType() {
        assertThatThrownBy(() -> loader.parse("id: x\nhost: x.test\nextractors:\n  - type: xpath\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadsEveryYamlFileOfADirectoryInAlphabeticalOrder(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("b-site.yml"), "id: b\nhost: b.test\nextractors: [{type: jsonld}]\n");
        Files.writeString(dir.resolve("a-site.yaml"), "id: a\nhost: a.test\nextractors: [{type: jsonld}]\n");
        Files.writeString(dir.resolve("README.md"), "ignored");

        List<SiteDefinition> sites = loader.loadAll(dir);

        assertThat(sites).extracting(SiteDefinition::id).containsExactly("a", "b");
        assertThat(loader.loadAll(dir.resolve("missing"))).isEmpty();
    }
}
