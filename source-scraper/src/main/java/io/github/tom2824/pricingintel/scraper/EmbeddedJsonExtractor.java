package io.github.tom2824.pricingintel.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.ObservedIdentity;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lit l'état applicatif que les frameworks front embarquent dans la page : un {@code <script id="__NEXT_DATA__">}
 * en JSON pur, ou une affectation {@code window.__INITIAL_STATE__ = {...}} dans un script quelconque.
 * Les champs sont adressés par pointeur JSON (RFC 6901), ex. {@code /props/pageProps/product/price}.
 */
public final class EmbeddedJsonExtractor implements Extractor {

    public static final String METHOD = "embedded-json";
    public static final double CONFIDENCE = 0.85;

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedJsonExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String scriptSelector;
    private final String variable;
    private final Map<String, String> paths;
    private final String defaultCurrency;

    /**
     * @param scriptSelector sélecteur CSS du script contenant du JSON pur, ou null
     * @param variable       nom de la variable affectée ({@code window.__INITIAL_STATE__}), ou null
     * @param paths          pointeurs JSON par champ ; {@code price} obligatoire, {@code listPrice}, {@code currency},
     *                       {@code availability}, {@code gtin}, {@code brand}, {@code mpn}, {@code sku}, {@code title} optionnels
     */
    public EmbeddedJsonExtractor(String scriptSelector, String variable, Map<String, String> paths, String defaultCurrency) {
        if ((scriptSelector == null) == (variable == null)) {
            throw new IllegalArgumentException("Exactly one of scriptSelector or variable must be set");
        }
        if (paths == null || !paths.containsKey("price")) {
            throw new IllegalArgumentException("paths.price is required");
        }
        paths.forEach((field, pointer) -> {
            if (!pointer.startsWith("/")) {
                throw new IllegalArgumentException("JSON pointer for '" + field + "' must start with '/', got '" + pointer + "'");
            }
        });
        this.scriptSelector = scriptSelector;
        this.variable = variable;
        this.paths = Map.copyOf(paths);
        this.defaultCurrency = defaultCurrency;
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public Optional<ExtractedOffer> extract(Document document) {
        String json = locateJson(document);
        if (json == null) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            LOG.debug("Embedded JSON on {} is not parseable: {}", document.location(), e.getOriginalMessage());
            return Optional.empty();
        }
        Optional<BigDecimal> price = PriceParser.parse(text(root, "price"));
        if (price.isEmpty()) {
            return Optional.empty();
        }
        String currency = Optional.ofNullable(text(root, "currency")).orElse(defaultCurrency);
        ObservedIdentity identity = new ObservedIdentity(text(root, "gtin"), text(root, "brand"),
                text(root, "mpn"), text(root, "sku"), text(root, "title"));

        return Optional.of(ExtractedOffer.builder(price.get(), currency, METHOD, CONFIDENCE)
                .listPrice(PriceParser.parse(text(root, "listPrice")).orElse(null))
                .availability(availability(root))
                .identity(identity)
                .build());
    }

    private String locateJson(Document document) {
        if (scriptSelector != null) {
            Element script = document.selectFirst(scriptSelector);
            return script == null ? null : script.data();
        }
        for (Element script : document.select("script")) {
            String data = script.data();
            int at = data.indexOf(variable);
            if (at < 0) {
                continue;
            }
            int equals = data.indexOf('=', at + variable.length());
            if (equals < 0) {
                continue;
            }
            String json = balancedJson(data, equals + 1);
            if (json != null) {
                return json;
            }
        }
        return null;
    }

    /** Extrait l'objet ou le tableau JSON qui commence au premier {@code {} ou {@code [} après {@code from}. */
    static String balancedJson(String text, int from) {
        int start = from;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        if (start >= text.length()) {
            return null;
        }
        char open = text.charAt(start);
        if (open != '{' && open != '[') {
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
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
                default -> {
                }
            }
        }
        return null;
    }

    private Availability availability(JsonNode root) {
        String pointer = paths.get("availability");
        if (pointer == null) {
            return Availability.UNKNOWN;
        }
        JsonNode node = root.at(pointer);
        if (node.isMissingNode() || node.isNull()) {
            return Availability.UNKNOWN;
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? Availability.IN_STOCK : Availability.OUT_OF_STOCK;
        }
        if (node.isNumber()) {
            return node.asDouble() > 0 ? Availability.IN_STOCK : Availability.OUT_OF_STOCK;
        }
        Availability fromSchema = SchemaOrg.availability(node.asText());
        return fromSchema != Availability.UNKNOWN ? fromSchema : CssExtractor.availabilityFromText(node.asText(), null, null);
    }

    private String text(JsonNode root, String field) {
        String pointer = paths.get(field);
        if (pointer == null) {
            return null;
        }
        JsonNode node = root.at(pointer);
        if (node.isMissingNode() || node.isNull() || node.isContainerNode()) {
            return null;
        }
        String text = node.asText();
        return text.isBlank() ? null : text;
    }
}
