package io.github.tom2824.pricingintel.sink.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Réduit une page HTML à l'essentiel, dans l'esprit de l'arbre d'accessibilité : ce qu'un lecteur voit
 * (titres, texte, listes, tableaux, caractéristiques, textes alternatifs, libellés ARIA), sans la navigation,
 * les scripts ni le style. Les blocs JSON embarqués sont conservés à part, intacts.
 *
 * <p>Sans navigateur, on n'a pas le vrai arbre ARIA : on s'en approche par la sémantique HTML
 * ({@code main}, {@code article}, rôles, {@code alt}, {@code aria-label}).
 */
public final class HtmlDistiller {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_JSON_BLOCKS = 20;

    /** Éléments retirés avant rendu : jamais du contenu produit. */
    private static final String DROPPED_SELECTOR = String.join(",",
            "script", "style", "noscript", "template", "svg", "iframe", "canvas", "video", "audio", "object", "embed",
            "nav", "header", "footer", "aside", "form", "select", "textarea",
            "[role=navigation]", "[role=banner]", "[role=contentinfo]", "[role=complementary]", "[role=dialog]",
            "[role=menu]", "[role=menubar]", "[role=search]", "[role=tooltip]",
            "[aria-hidden=true]", "[hidden]");

    private static final Set<String> PARAGRAPH_TAGS = Set.of("p", "blockquote", "pre", "figure", "figcaption",
            "details", "summary", "fieldset", "legend", "address");
    private static final Set<String> BLOCK_TAGS = Set.of("div", "section", "article", "main", "li", "td", "th",
            "tr", "thead", "tbody", "tfoot", "caption", "body", "html", "button", "label", "dt", "dd");

    /** Affectations globales dont la valeur est un objet ou un tableau JSON : {@code window.__INITIAL_STATE__ = {...}}. */
    private static final Pattern GLOBAL_ASSIGNMENT = Pattern.compile(
            "(?:window|self|globalThis)\\.(__[A-Za-z0-9_]+__|[A-Za-z_$][\\w$]*(?:State|Data|Props|Store|Config))\\s*=\\s*(?=[\\[{])");

    public DistilledPage distill(String html, String baseUri) {
        Document document = Jsoup.parse(html == null ? "" : html, baseUri == null ? "" : baseUri);
        List<DistilledPage.JsonBlock> blocks = extractJsonBlocks(document);
        String title = document.title();
        if (title.isBlank()) {
            Element h1 = document.selectFirst("h1");
            title = h1 == null ? "" : h1.text();
        }
        return new DistilledPage(title.strip(), blocks, toMarkdown(document));
    }

    static List<DistilledPage.JsonBlock> extractJsonBlocks(Document document) {
        List<DistilledPage.JsonBlock> blocks = new ArrayList<>();
        for (Element script : document.select("script[type=application/ld+json]")) {
            addBlock(blocks, "ld+json", parseOrText(script.data()));
        }
        for (Element script : document.select("script[type=application/json]")) {
            JsonNode node = parse(script.data());
            if (node != null) {
                String id = script.id();
                addBlock(blocks, id.isEmpty() ? "application/json" : "#" + id, node);
            }
        }
        for (Element script : document.select("script:not([type]), script[type=text/javascript], script[type=module]")) {
            String data = script.data();
            Matcher matcher = GLOBAL_ASSIGNMENT.matcher(data);
            while (matcher.find()) {
                String json = balancedJson(data, matcher.end());
                JsonNode node = json == null ? null : parse(json);
                if (node != null) {
                    addBlock(blocks, "window." + matcher.group(1), node);
                }
            }
        }
        return blocks;
    }

    private static void addBlock(List<DistilledPage.JsonBlock> blocks, String source, JsonNode content) {
        if (blocks.size() < MAX_JSON_BLOCKS) {
            blocks.add(new DistilledPage.JsonBlock(source, content));
        }
    }

    private static JsonNode parseOrText(String text) {
        JsonNode node = parse(text);
        return node != null ? node : JsonNodeFactory.instance.textNode(text.strip());
    }

    private static JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** Extrait l'objet ou le tableau JSON équilibré qui commence à {@code from} (accolades dans les chaînes ignorées). */
    static String balancedJson(String text, int from) {
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

    static String toMarkdown(Document document) {
        Element root = document.selectFirst("main, [role=main], article");
        if (root == null) {
            root = document.body();
        }
        root.select(DROPPED_SELECTOR).remove();
        StringBuilder sb = new StringBuilder();
        renderChildren(root, sb);
        return tidy(sb.toString());
    }

    private static void renderChildren(Element parent, StringBuilder sb) {
        for (Node child : parent.childNodes()) {
            if (child instanceof TextNode text) {
                appendText(sb, text.getWholeText());
            } else if (child instanceof Element element) {
                render(element, sb);
            }
        }
    }

    private static void render(Element e, StringBuilder sb) {
        String tag = e.normalName();
        // Un élément sans texte mais avec un libellé ARIA (bouton icône) : c'est le libellé qu'un lecteur entend.
        String label = e.attr("aria-label").strip();
        if (!label.isEmpty() && !tag.equals("img") && e.text().isBlank()) {
            appendText(sb, " [" + label + "] ");
            return;
        }
        switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                paragraph(sb);
                sb.append("#".repeat(tag.charAt(1) - '0')).append(' ').append(inline(e));
                paragraph(sb);
            }
            case "ul", "ol" -> {
                paragraph(sb);
                int index = 1;
                for (Element item : e.children()) {
                    if (!item.normalName().equals("li")) {
                        continue;
                    }
                    line(sb);
                    sb.append(tag.equals("ol") ? index++ + ". " : "- ").append(inline(item));
                }
                paragraph(sb);
            }
            case "dl" -> {
                paragraph(sb);
                for (Element item : e.children()) {
                    if (item.normalName().equals("dt")) {
                        line(sb);
                        sb.append("- **").append(inline(item)).append("**");
                    } else if (item.normalName().equals("dd")) {
                        sb.append(" : ").append(inline(item));
                    }
                }
                paragraph(sb);
            }
            case "table" -> {
                paragraph(sb);
                boolean headerDone = false;
                for (Element row : e.select("tr")) {
                    List<String> cells = row.children().stream()
                            .filter(c -> c.normalName().equals("td") || c.normalName().equals("th"))
                            .map(c -> inline(c).replace("|", "\\|"))
                            .toList();
                    if (cells.isEmpty()) {
                        continue;
                    }
                    line(sb);
                    sb.append("| ").append(String.join(" | ", cells)).append(" |");
                    if (!headerDone) {
                        line(sb);
                        sb.append("|").append(" --- |".repeat(cells.size()));
                        headerDone = true;
                    }
                }
                paragraph(sb);
            }
            case "img" -> {
                String alt = e.attr("alt").strip();
                if (!alt.isEmpty()) {
                    appendText(sb, " ![" + alt + "] ");
                }
            }
            case "br" -> line(sb);
            case "hr" -> {
                paragraph(sb);
                sb.append("---");
                paragraph(sb);
            }
            default -> {
                if (PARAGRAPH_TAGS.contains(tag)) {
                    paragraph(sb);
                    renderChildren(e, sb);
                    paragraph(sb);
                } else if (BLOCK_TAGS.contains(tag)) {
                    line(sb);
                    renderChildren(e, sb);
                    line(sb);
                } else {
                    renderChildren(e, sb);
                }
            }
        }
    }

    private static String inline(Element e) {
        StringBuilder sb = new StringBuilder();
        renderChildren(e, sb);
        return sb.toString().replaceAll("\\s+", " ").strip();
    }

    private static void appendText(StringBuilder sb, String text) {
        String collapsed = text.replace(' ', ' ').replaceAll("\\s+", " ");
        if (collapsed.isBlank()) {
            if (!sb.isEmpty() && !Character.isWhitespace(sb.charAt(sb.length() - 1))) {
                sb.append(' ');
            }
            return;
        }
        sb.append(collapsed);
    }

    private static void line(StringBuilder sb) {
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
    }

    private static void paragraph(StringBuilder sb) {
        if (sb.isEmpty()) {
            return;
        }
        line(sb);
        if (sb.length() < 2 || sb.charAt(sb.length() - 2) != '\n') {
            sb.append('\n');
        }
    }

    private static String tidy(String markdown) {
        StringBuilder out = new StringBuilder();
        int blankRun = 0;
        for (String rawLine : markdown.split("\n")) {
            String line = rawLine.replaceAll("\\s+", " ").strip();
            if (line.isEmpty()) {
                blankRun++;
                continue;
            }
            if (!out.isEmpty()) {
                out.append(blankRun > 0 ? "\n\n" : "\n");
            }
            out.append(line);
            blankRun = 0;
        }
        return out.toString();
    }
}
