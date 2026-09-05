package io.github.tom2824.pricingintel.scraper;

import io.github.tom2824.pricingintel.domain.Availability;
import io.github.tom2824.pricingintel.domain.ObservedIdentity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Sélecteurs CSS déclarés site par site. Dernier recours : ça marche partout, mais ça casse à chaque refonte.
 * Syntaxe : un sélecteur Jsoup, éventuellement suivi de {@code @attribut} pour lire un attribut plutôt que le texte,
 * ex. {@code meta[itemprop=price] @content}.
 */
public final class CssExtractor implements Extractor {

    public static final String METHOD = "css";
    public static final double CONFIDENCE = 0.7;

    static final List<String> DEFAULT_IN_STOCK = List.of("en stock", "disponible", "in stock", "available", "expédié");
    static final List<String> DEFAULT_OUT_OF_STOCK = List.of("rupture", "indisponible", "épuisé", "out of stock",
            "sold out", "unavailable", "plus disponible");

    private final String priceSelector;
    private final String listPriceSelector;
    private final String availabilitySelector;
    private final List<String> inStockKeywords;
    private final List<String> outOfStockKeywords;
    private final String gtinSelector;
    private final String brandSelector;
    private final String mpnSelector;
    private final String skuSelector;
    private final String titleSelector;
    private final String currency;

    public CssExtractor(ExtractorSpec.Css spec, String defaultCurrency) {
        if (spec.price() == null || spec.price().isBlank()) {
            throw new IllegalArgumentException("css extractor requires a 'price' selector");
        }
        this.priceSelector = spec.price();
        this.listPriceSelector = spec.listPrice();
        this.availabilitySelector = spec.availability();
        this.inStockKeywords = lower(spec.inStock() == null ? DEFAULT_IN_STOCK : spec.inStock());
        this.outOfStockKeywords = lower(spec.outOfStock() == null ? DEFAULT_OUT_OF_STOCK : spec.outOfStock());
        this.gtinSelector = spec.gtin();
        this.brandSelector = spec.brand();
        this.mpnSelector = spec.mpn();
        this.skuSelector = spec.sku();
        this.titleSelector = spec.title();
        this.currency = spec.currency() == null ? defaultCurrency : spec.currency();
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public Optional<ExtractedOffer> extract(Document document) {
        Optional<BigDecimal> price = read(document, priceSelector).flatMap(PriceParser::parse);
        if (price.isEmpty()) {
            return Optional.empty();
        }
        Availability availability = read(document, availabilitySelector)
                .map(text -> availabilityFromText(text, inStockKeywords, outOfStockKeywords))
                .orElse(Availability.UNKNOWN);
        ObservedIdentity identity = new ObservedIdentity(
                read(document, gtinSelector).orElse(null),
                read(document, brandSelector).orElse(null),
                read(document, mpnSelector).orElse(null),
                read(document, skuSelector).orElse(null),
                read(document, titleSelector).orElse(null));

        return Optional.of(ExtractedOffer.builder(price.get(), currency, METHOD, CONFIDENCE)
                .listPrice(read(document, listPriceSelector).flatMap(PriceParser::parse).orElse(null))
                .availability(availability)
                .identity(identity)
                .build());
    }

    /** "Indisponible" contient "disponible" : les mots-clés de rupture sont testés en premier. */
    static Availability availabilityFromText(String text, List<String> inStock, List<String> outOfStock) {
        String lowered = text.toLowerCase(Locale.ROOT);
        List<String> out = outOfStock == null ? DEFAULT_OUT_OF_STOCK : outOfStock;
        List<String> in = inStock == null ? DEFAULT_IN_STOCK : inStock;
        if (out.stream().anyMatch(lowered::contains)) {
            return Availability.OUT_OF_STOCK;
        }
        if (lowered.contains("précommande") || lowered.contains("pre-order") || lowered.contains("preorder")) {
            return Availability.PREORDER;
        }
        if (in.stream().anyMatch(lowered::contains)) {
            return Availability.IN_STOCK;
        }
        return Availability.UNKNOWN;
    }

    static Optional<String> read(Document document, String selectorSpec) {
        if (selectorSpec == null || selectorSpec.isBlank()) {
            return Optional.empty();
        }
        String selector = selectorSpec;
        String attribute = null;
        int at = selectorSpec.lastIndexOf(" @");
        if (at > 0) {
            selector = selectorSpec.substring(0, at).strip();
            attribute = selectorSpec.substring(at + 2).strip();
        }
        Element element = document.selectFirst(selector);
        if (element == null) {
            return Optional.empty();
        }
        String value = attribute == null ? element.text() : element.attr(attribute);
        return value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }

    private static List<String> lower(List<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT)).toList();
    }
}
