package io.github.tom2824.pricingintel.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.tom2824.pricingintel.collector.ListingProvider;
import io.github.tom2824.pricingintel.domain.Listing;
import io.github.tom2824.pricingintel.domain.ListingId;
import io.github.tom2824.pricingintel.domain.ProductId;
import io.github.tom2824.pricingintel.domain.SourceId;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Les annonces à relever, déclarées dans un fichier YAML :
 * <pre>
 * listings:
 *   - id: ldlc-rtx4070s-msi
 *     product: rtx-4070-super-msi-ventus-2x
 *     source: ldlc
 *     url: https://www.ldlc.com/fiche/PB00xxxxx.html
 * </pre>
 * Le matching produit ↔ annonce est volontairement manuel dans cette version : voir l'ADR 0008.
 */
public final class YamlListingProvider implements ListingProvider {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private final Path file;

    public YamlListingProvider(Path file) {
        this.file = file;
    }

    @Override
    public List<Listing> listings() {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Listings file not found: " + file.toAbsolutePath()
                    + " (copy config/listings.example.yml to get started)");
        }
        ListingsFile parsed;
        try {
            parsed = YAML.readValue(file.toFile(), ListingsFile.class);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid listings file " + file + ": " + e.getMessage(), e);
        }
        if (parsed.listings() == null || parsed.listings().isEmpty()) {
            throw new IllegalStateException("Listings file " + file + " declares no listing");
        }
        Set<String> ids = new HashSet<>();
        return parsed.listings().stream().map(entry -> {
            if (!ids.add(entry.id())) {
                throw new IllegalStateException("Duplicate listing id '" + entry.id() + "' in " + file);
            }
            return entry.toListing();
        }).toList();
    }

    record ListingsFile(List<Entry> listings) {
    }

    record Entry(String id, String product, String source, String url, String externalRef) {
        Listing toListing() {
            try {
                return new Listing(new ListingId(id), new ProductId(product), new SourceId(source), URI.create(url), externalRef);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Invalid listing '" + id + "': " + e.getMessage(), e);
            }
        }
    }
}
