package io.github.tom2824.pricingintel.sink.file;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Ce qu'on garde d'une page une fois distillée : son titre, les blocs JSON qu'elle embarquait (données
 * structurées et état applicatif, tels quels) et une version Markdown de son contenu visible.
 * Quelques kilo-octets au lieu de cent, sans perdre ce qui alimente les extracteurs fiables.
 */
public record DistilledPage(String title, List<JsonBlock> jsonBlocks, String markdown) {

    public DistilledPage {
        jsonBlocks = List.copyOf(jsonBlocks);
        markdown = markdown == null ? "" : markdown;
    }

    /**
     * @param source origine du bloc : {@code ld+json}, {@code #__NEXT_DATA__} (id du script) ou le nom de la
     *               variable globale affectée ({@code window.__INITIAL_STATE__})
     */
    public record JsonBlock(String source, JsonNode content) {
    }
}
