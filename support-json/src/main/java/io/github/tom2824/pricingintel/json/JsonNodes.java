package io.github.tom2824.pricingintel.json;

import com.fasterxml.jackson.databind.JsonNode;

/** Accès aux champs d'un arbre JSON venu d'une page : absent, nul, conteneur ou vide, c'est « pas de valeur ». */
public final class JsonNodes {

    private JsonNodes() {
    }

    /** @return le texte du nœud, ou {@code null} s'il est absent, nul, conteneur (objet, tableau) ou blanc */
    public static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.isContainerNode()) {
            return null;
        }
        String text = node.asText();
        return text.isBlank() ? null : text;
    }

    /** Le champ {@code key} de {@code node}, par {@link #textOrNull(JsonNode)} ; {@code null} si {@code node} est nul. */
    public static String textOrNull(JsonNode node, String key) {
        return node == null ? null : textOrNull(node.get(key));
    }
}
