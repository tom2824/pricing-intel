package io.github.tom2824.pricingintel.json;

/**
 * Découpe d'un bloc JSON dans du texte qui n'en est pas (un script, une affectation JavaScript) : on part du
 * premier {@code {} ou {@code [} et on s'arrête à sa fermeture, en ignorant accolades et crochets à l'intérieur
 * des chaînes. Une seule implémentation pour le scraper et l'archive distillée : un parseur d'accolades qui
 * existe en deux exemplaires finit toujours par diverger.
 */
public final class JsonText {

    private JsonText() {
    }

    /**
     * @param text texte contenant du JSON
     * @param from position à partir de laquelle chercher l'ouverture (les blancs sont sautés)
     * @return le bloc équilibré, ou {@code null} si rien ne commence par {@code {} ou {@code [} ou si le bloc reste ouvert
     */
    public static String balanced(String text, int from) {
        int start = from;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        if (start >= text.length() || (text.charAt(start) != '{' && text.charAt(start) != '[')) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{', '[' -> depth++;
                case '}', ']' -> {
                    if (--depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
                default -> {
                }
            }
        }
        return null;
    }
}
