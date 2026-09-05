package io.github.tom2824.pricingintel.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.ItemCondition;
import io.github.tom2824.pricingintel.domain.ObservedIdentity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lit les blocs {@code <script type="application/ld+json">} et cherche un {@code Product} schema.org avec ses
 * {@code offers}. C'est la méthode la plus fiable : les sites maintiennent ces données pour Google Shopping,
 * et elles portent souvent le GTIN, la marque et la référence fabricant.
 */
public final class JsonLdExtractor implements Extractor {

    public static final String METHOD = "jsonld";
    public static final double CONFIDENCE = 0.95;

    private static final Logger LOG = LoggerFactory.getLogger(JsonLdExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> NESTING_KEYS = List.of("@graph", "mainEntity", "itemListElement", "item");
    private static final List<String> GTIN_KEYS = List.of("gtin13", "gtin", "gtin14", "gtin12", "gtin8");

    private final String defaultCurrency;

    public JsonLdExtractor(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public Optional<ExtractedOffer> extract(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            JsonNode root;
            try {
                root = MAPPER.readTree(script.data());
            } catch (JsonProcessingException e) {
                LOG.debug("Skipping unparseable JSON-LD block on {}: {}", document.location(), e.getOriginalMessage());
                continue;
            }
            Optional<ExtractedOffer> offer = findProduct(root).flatMap(this::toOffer);
            if (offer.isPresent()) {
                return offer;
            }
        }
        return Optional.empty();
    }

    static Optional<JsonNode> findProduct(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<JsonNode> found = findProduct(child);
                if (found.isPresent()) {
                    return found;
                }
            }
            return Optional.empty();
        }
        if (node.isObject()) {
            if (hasType(node, "Product")) {
                return Optional.of(node);
            }
            for (String key : NESTING_KEYS) {
                if (node.has(key)) {
                    Optional<JsonNode> found = findProduct(node.get(key));
                    if (found.isPresent()) {
                        return found;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean hasType(JsonNode node, String type) {
        JsonNode typeNode = node.get("@type");
        if (typeNode == null) {
            return false;
        }
        if (typeNode.isArray()) {
            for (JsonNode t : typeNode) {
                if (typeMatches(t.asText(), type)) {
                    return true;
                }
            }
            return false;
        }
        return typeMatches(typeNode.asText(), type);
    }

    private static boolean typeMatches(String actual, String expected) {
        return actual.equalsIgnoreCase(expected) || actual.endsWith(expected);
    }

    private Optional<ExtractedOffer> toOffer(JsonNode product) {
        JsonNode offer = pickOffer(product.get("offers"));
        if (offer == null) {
            return Optional.empty();
        }
        Optional<BigDecimal> price = PriceParser.parse(text(offer, "price"))
                .or(() -> PriceParser.parse(text(offer, "lowPrice")))
                .or(() -> PriceParser.parse(text(offer.path("priceSpecification"), "price")));
        if (price.isEmpty()) {
            return Optional.empty();
        }
        String currency = Optional.ofNullable(text(offer, "priceCurrency"))
                .or(() -> Optional.ofNullable(text(offer.path("priceSpecification"), "priceCurrency")))
                .orElse(defaultCurrency);
        Availability availability = SchemaOrg.availability(text(offer, "availability"));
        ItemCondition condition = SchemaOrg.condition(text(offer, "itemCondition"));
        if (condition == ItemCondition.UNKNOWN) {
            condition = SchemaOrg.condition(text(product, "itemCondition"));
        }
        ObservedIdentity identity = new ObservedIdentity(
                firstText(product, GTIN_KEYS), brandName(product.get("brand")),
                text(product, "mpn"), text(product, "sku"), text(product, "name"));

        return Optional.of(ExtractedOffer.builder(price.get(), currency, METHOD, CONFIDENCE)
                .availability(availability)
                .condition(condition)
                .identity(identity)
                .build());
    }

    /** Une offre unique, ou dans une liste la première en stock, sinon la première. */
    private static JsonNode pickOffer(JsonNode offers) {
        if (offers == null || offers.isNull()) {
            return null;
        }
        if (offers.isObject()) {
            return offers;
        }
        if (offers.isArray() && !offers.isEmpty()) {
            for (JsonNode candidate : offers) {
                if (SchemaOrg.availability(text(candidate, "availability")) == Availability.IN_STOCK) {
                    return candidate;
                }
            }
            return offers.get(0);
        }
        return null;
    }

    private static String brandName(JsonNode brand) {
        if (brand == null || brand.isNull()) {
            return null;
        }
        if (brand.isTextual()) {
            return brand.asText();
        }
        if (brand.isArray() && !brand.isEmpty()) {
            return brandName(brand.get(0));
        }
        return text(brand, "name");
    }

    private static String firstText(JsonNode node, List<String> keys) {
        for (String key : keys) {
            String value = text(node, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull() || value.isContainerNode()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
