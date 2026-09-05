package io.github.tom2824.pricingintel.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Charge un fichier YAML de catalogue (produits, identifiants, annonces) dans la base. Idempotent : un produit
 * est reconnu par sa clé naturelle, une annonce par son code ; relancer l'import met à jour au lieu de dupliquer.
 * Une annonce qui déclare un produit reçoit une correspondance validée manuelle (ADR 0016) ; si elle en avait
 * une vers un autre produit, l'ancienne est clôturée, pas effacée.
 */
public class CatalogueImporter {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogueImporter.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private final ProductFamilyRepository families;
    private final ProductRepository products;
    private final ProductIdentifierRepository identifiers;
    private final SourceRepository sources;
    private final ListingRepository listings;
    private final ListingMatchRepository matches;
    private final Clock clock;

    public CatalogueImporter(ProductFamilyRepository families, ProductRepository products,
                             ProductIdentifierRepository identifiers, SourceRepository sources,
                             ListingRepository listings, ListingMatchRepository matches, Clock clock) {
        this.families = families;
        this.products = products;
        this.identifiers = identifiers;
        this.sources = sources;
        this.listings = listings;
        this.matches = matches;
        this.clock = clock;
    }

    public record Result(int productsCreated, int productsUpdated, int listingsCreated, int listingsUpdated, int matchesCreated) {
        public String summary() {
            return "%d produit(s) créé(s), %d mis à jour ; %d annonce(s) créée(s), %d mise(s) à jour ; %d correspondance(s) créée(s)"
                    .formatted(productsCreated, productsUpdated, listingsCreated, listingsUpdated, matchesCreated);
        }
    }

    public static CatalogueFile parse(Path file) {
        try {
            return YAML.readValue(file.toFile(), CatalogueFile.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid catalogue file " + file + ": " + e.getMessage(), e);
        }
    }

    @Transactional
    public Result importFile(Path file) {
        return importCatalogue(parse(file));
    }

    @Transactional
    public Result importCatalogue(CatalogueFile file) {
        int productsCreated = 0;
        int productsUpdated = 0;
        int listingsCreated = 0;
        int listingsUpdated = 0;
        int matchesCreated = 0;

        Map<String, ProductEntity> byKey = new HashMap<>();
        for (CatalogueFile.ProductSpec spec : file.products()) {
            requireText(spec.key(), "products[].key");
            if (byKey.containsKey(spec.key())) {
                throw new IllegalArgumentException("Duplicate product key '" + spec.key() + "' in catalogue file");
            }
            boolean created = importProduct(spec, byKey);
            if (created) {
                productsCreated++;
            } else {
                productsUpdated++;
            }
        }

        for (CatalogueFile.ListingSpec spec : file.listings()) {
            requireText(spec.code(), "listings[].code");
            requireText(spec.url(), "listings[].url");
            SourceEntity source = sources.findById(requireText(spec.source(), "listings[].source"))
                    .orElseGet(() -> {
                        LOG.warn("Source '{}' inconnue : créée comme site scrapé, à compléter", spec.source());
                        return sources.save(new SourceEntity(spec.source(), spec.source(), "scraper", null));
                    });
            boolean active = spec.active() == null || spec.active();
            Optional<ListingEntity> existing = listings.findByCode(spec.code());
            ListingEntity listing;
            if (existing.isPresent()) {
                listing = existing.get();
                listing.update(source, spec.url(), spec.externalRef(), active);
                listingsUpdated++;
            } else {
                listing = listings.save(new ListingEntity(spec.code(), source, spec.url(), spec.externalRef()));
                listing.update(source, spec.url(), spec.externalRef(), active);
                listingsCreated++;
            }
            if (spec.product() != null) {
                ProductEntity product = byKey.get(spec.product());
                if (product == null) {
                    throw new IllegalArgumentException("Listing '" + spec.code() + "' references unknown product key '" + spec.product() + "'");
                }
                if (ensureValidatedMatch(listing, product)) {
                    matchesCreated++;
                }
            }
        }

        Result result = new Result(productsCreated, productsUpdated, listingsCreated, listingsUpdated, matchesCreated);
        LOG.info("Import du catalogue : {}", result.summary());
        return result;
    }

    private boolean importProduct(CatalogueFile.ProductSpec spec, Map<String, ProductEntity> byKey) {
        ProductFamilyEntity family = families.findById(requireText(spec.family(), "products[].family"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown family '" + spec.family() + "' for product '" + spec.key() + "'"));
        requireText(spec.brand(), "products[].brand");
        requireText(spec.name(), "products[].name");
        AttributeSchema schema = family.schema();
        Map<String, Object> attributes;
        try {
            attributes = new LinkedHashMap<>(schema.normalize(spec.attributes()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Product '" + spec.key() + "': " + e.getMessage(), e);
        }
        String naturalKey = Keys.naturalKey(family.getCode(), spec.brand(), spec.mpn(), spec.name(), attributes, schema);
        String equivalenceKey = Keys.equivalenceKey(family.getCode(), attributes, schema);

        Optional<ProductEntity> existing = products.findByNaturalKey(naturalKey);
        ProductEntity product;
        boolean created;
        if (existing.isPresent()) {
            product = existing.get();
            product.update(spec.name(), attributes, equivalenceKey, spec.purchasePrice(), spec.currentPrice(), spec.currency());
            created = false;
        } else {
            product = new ProductEntity(family, spec.brand().strip(), blankToNull(spec.mpn()), spec.name().strip(),
                    attributes, naturalKey, equivalenceKey, spec.currency());
            product.setPrices(spec.purchasePrice(), spec.currentPrice());
            product = products.save(product);
            created = true;
        }

        if (product.getMpn() != null) {
            attachIdentifier(product, ProductIdentifierEntity.SCHEME_MPN, Keys.normalize(product.getBrand()) + ":" + Keys.normalize(product.getMpn()));
        }
        for (CatalogueFile.IdentifierSpec identifier : spec.identifiers()) {
            String scheme = requireText(identifier.scheme(), "identifiers[].scheme").toLowerCase(Locale.ROOT);
            String value = requireText(identifier.value(), "identifiers[].value");
            String normalized = scheme.equals(ProductIdentifierEntity.SCHEME_GTIN) ? Gtin.normalize(value) : value.strip();
            attachIdentifier(product, scheme, normalized);
        }
        byKey.put(spec.key(), product);
        return created;
    }

    /** Un identifiant appartient à un seul produit : un conflit est une erreur à trancher par un humain (ADR 0015). */
    private void attachIdentifier(ProductEntity product, String scheme, String value) {
        Optional<ProductIdentifierEntity> existing = identifiers.findBySchemeAndValue(scheme, value);
        if (existing.isPresent()) {
            ProductEntity owner = existing.get().getProduct();
            if (!owner.getId().equals(product.getId())) {
                throw new IllegalStateException("Identifier " + scheme + ":" + value + " already belongs to product #"
                        + owner.getId() + " (" + owner.getName() + "), cannot attach it to " + product.getName());
            }
            existing.get().confirm();
            return;
        }
        product.addIdentifier(scheme, value, "manual", true);
    }

    private boolean ensureValidatedMatch(ListingEntity listing, ProductEntity product) {
        Optional<ListingMatchEntity> current = matches.findCurrentValidated(listing.getId());
        if (current.isPresent()) {
            if (current.get().getProduct().getId().equals(product.getId())) {
                return false;
            }
            current.get().close(clock.instant(), "Remplacée par un import de catalogue");
        }
        matches.save(new ListingMatchEntity(listing, product, ListingMatchEntity.STATUS_VALIDATED,
                ListingMatchEntity.METHOD_MANUAL, BigDecimal.ONE,
                Map.of("source", "catalogue import", "productKey", product.getNaturalKey()),
                "import", clock.instant()));
        return true;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Catalogue file: '" + field + "' is required");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
