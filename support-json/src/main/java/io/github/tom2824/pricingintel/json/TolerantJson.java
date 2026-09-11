package io.github.tom2824.pricingintel.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Le parseur pour du JSON écrit par des sites, pas par des machines : des retours à la ligne bruts dans les
 * chaînes (vu chez Cybertek), une virgule de trop en fin de liste. On lit ce qu'on peut plutôt que de perdre
 * une page pour une virgule.
 */
public final class TolerantJson {

    /** Immuable et thread-safe : un seul exemplaire pour tout le processus. */
    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();

    private TolerantJson() {
    }

    /** @return l'arbre lu, ou {@code null} si le texte est vide ou n'est pas du JSON, même tolérant */
    public static JsonNode readOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
