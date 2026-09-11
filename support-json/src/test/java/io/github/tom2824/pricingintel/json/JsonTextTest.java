package io.github.tom2824.pricingintel.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class JsonTextTest {

    @Test
    void balancedStopsAtTheMatchingBracketAndIgnoresBracketsInsideStrings() {
        assertThat(JsonText.balanced("x = {\"a\":[1,{\"b\":\"}\"}]} ; var y = 2;", 3)).isEqualTo("{\"a\":[1,{\"b\":\"}\"}]}");
        assertThat(JsonText.balanced("= [1, 2, [3]] // fin", 1)).isEqualTo("[1, 2, [3]]");
        assertThat(JsonText.balanced("= {\"quoted\": \"\\\"}\"} suite", 1)).isEqualTo("{\"quoted\": \"\\\"}\"}");
    }

    @Test
    void balancedRefusesWhatIsNotAnObjectOrAnArray() {
        assertThat(JsonText.balanced("= 42", 1)).isNull();
        assertThat(JsonText.balanced("   ", 0)).isNull();
        assertThat(JsonText.balanced("= {\"open\": 1", 1)).as("bloc jamais fermé").isNull();
    }

    @Test
    void tolerantParserAcceptsRawNewlinesAndTrailingCommas() {
        JsonNode node = TolerantJson.readOrNull("{\"name\": \"ligne 1\nligne 2\", \"tags\": [\"a\", \"b\",],}");

        assertThat(node).isNotNull();
        assertThat(node.get("name").asText()).isEqualTo("ligne 1\nligne 2");
        assertThat(node.get("tags")).hasSize(2);
        assertThat(TolerantJson.readOrNull("pas du json")).isNull();
        assertThat(TolerantJson.readOrNull("  ")).isNull();
    }

    @Test
    void textOrNullTreatsMissingNullContainerAndBlankAsAbsent() {
        JsonNode node = TolerantJson.readOrNull("{\"s\": \"x\", \"n\": null, \"o\": {}, \"a\": [], \"b\": \"  \", \"i\": 3}");

        assertThat(JsonNodes.textOrNull(node, "s")).isEqualTo("x");
        assertThat(JsonNodes.textOrNull(node, "i")).isEqualTo("3");
        assertThat(JsonNodes.textOrNull(node, "n")).isNull();
        assertThat(JsonNodes.textOrNull(node, "o")).isNull();
        assertThat(JsonNodes.textOrNull(node, "a")).isNull();
        assertThat(JsonNodes.textOrNull(node, "b")).isNull();
        assertThat(JsonNodes.textOrNull(node, "missing")).isNull();
        assertThat(JsonNodes.textOrNull(null, "s")).isNull();
        assertThat(JsonNodes.textOrNull(node.at("/o/deep"))).as("pointeur vers un nœud manquant").isNull();
    }
}
