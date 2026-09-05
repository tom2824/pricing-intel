package io.github.tom2824.pricingintel.sink.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.collector.RawSnapshotStore;
import io.github.tom2824.pricingintel.domain.ListingId;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Archive une version distillée de chaque page sous {@code <racine>/<annonce>/<horodatage>.distilled.json.gz} :
 * métadonnées de la réponse, blocs JSON embarqués intacts, contenu visible en Markdown. Dix à trente fois plus
 * léger que le HTML, lisible tel quel, et suffisant pour rejouer les extracteurs JSON. C'est l'archive de long terme.
 */
public final class DistilledSnapshotStore implements RawSnapshotStore {

    static final String SUFFIX = ".distilled.json.gz";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final Path root;
    private final Duration retention;
    private final HtmlDistiller distiller = new HtmlDistiller();

    /** @param retention âge au-delà duquel {@link #purgeExpired} supprime les archives, ou null pour ne jamais purger */
    public DistilledSnapshotStore(Path root, Duration retention) {
        this.root = root;
        this.retention = retention;
    }

    @Override
    public void store(ListingId listingId, FetchResult result) {
        Path dir = root.resolve(SnapshotRetention.safeName(listingId.value()));
        Path file = dir.resolve(SnapshotRetention.stamp(result.fetchedAt()) + SUFFIX);
        DistilledPage page = distiller.distill(result.body(), result.finalUri().toString());

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("listingId", listingId.value());
        document.put("requestedUri", result.requestedUri().toString());
        document.put("finalUri", result.finalUri().toString());
        document.put("status", result.status());
        document.put("contentType", result.contentType());
        document.put("fetchedAt", result.fetchedAt().toString());
        document.put("htmlLength", result.body().length());
        document.put("title", page.title());
        document.put("jsonBlocks", page.jsonBlocks());
        document.put("markdown", page.markdown());

        try {
            Files.createDirectories(dir);
            try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
                MAPPER.writeValue(out, document);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot archive distilled snapshot of " + listingId + " under " + dir, e);
        }
    }

    @Override
    public int purgeExpired(Instant now) {
        return SnapshotRetention.purge(root, name -> name.endsWith(SUFFIX), retention, now);
    }
}
