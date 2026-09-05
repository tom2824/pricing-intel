package io.github.tom2824.pricingintel.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Format du fichier YAML de catalogue :
 * <pre>
 * products:
 *   - key: rtx4070s-msi-ventus          # référence locale au fichier, utilisée par les annonces
 *     family: gpu
 *     brand: MSI
 *     mpn: RTX 4070 SUPER 12G VENTUS 2X OC
 *     name: MSI GeForce RTX 4070 SUPER 12G VENTUS 2X OC
 *     attributes: {chipset: RTX 4070 SUPER, vram_gb: 12}
 *     identifiers: [{scheme: gtin, value: "4711377114363"}]
 *     purchasePrice: 520
 *     currentPrice: 599.99
 * listings:
 *   - code: ldlc-rtx4070s-msi-ventus
 *     product: rtx4070s-msi-ventus       # optionnel : sans produit, l'annonce est relevée mais non appariée
 *     source: ldlc
 *     url: https://www.ldlc.com/fiche/PB00584657.html
 * </pre>
 */
public record CatalogueFile(List<ProductSpec> products, List<ListingSpec> listings) {

    public CatalogueFile {
        products = products == null ? List.of() : List.copyOf(products);
        listings = listings == null ? List.of() : List.copyOf(listings);
    }

    public record ProductSpec(
            String key,
            String family,
            String brand,
            String mpn,
            String name,
            Map<String, Object> attributes,
            List<IdentifierSpec> identifiers,
            BigDecimal purchasePrice,
            BigDecimal currentPrice,
            String currency) {

        public ProductSpec {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
            identifiers = identifiers == null ? List.of() : List.copyOf(identifiers);
            currency = currency == null || currency.isBlank() ? "EUR" : currency;
        }
    }

    public record IdentifierSpec(String scheme, String value) {
    }

    public record ListingSpec(String code, String product, String source, String url, String externalRef, Boolean active) {
    }
}
