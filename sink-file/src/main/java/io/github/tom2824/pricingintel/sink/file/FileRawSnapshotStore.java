package io.github.tom2824.pricingintel.sink.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tom2824.pricingintel.collector.FetchResult;
import io.github.tom2824.pricingintel.collector.RawSnapshotStore;
import io.github.tom2824.pricingintel.domain.ListingId;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Archive chaque réponse HTML complète sous {@code <racine>/<annonce>/<horodatage>.html.gz}, avec un fichier
 * {@code .meta.json} à côté (statut, URL finale, type de contenu). C'est la version lourde, utile pour déboguer
 * un extracteur qui casse ; sa rétention est courte. Pour le long terme, voir {@link DistilledSnapshotStore}.
 */
public final class FileRawSnapshotStore implements RawSnapshotStore {

    static final String HTML_SUFFIX = ".html.gz";
    static final String META_SUFFIX = ".meta.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path root;
    private final Duration retention;

    /** Sans rétention : rien n'est jamais purgé. */
    public FileRawSnapshotStore(Path root) {
        this(root, null);
    }

    /** @param retention âge au-delà duquel {@link #purgeExpired} supprime les archives, ou null pour ne jamais purger */
    public FileRawSnapshotStore(Path root, Duration retention) {
        this.root = root;
        this.retention = retention;
    }

    @Override
    public void store(ListingId listingId, FetchResult result) {
        Path dir = root.resolve(SnapshotRetention.safeName(listingId.value()));
        String stamp = SnapshotRetention.stamp(result.fetchedAt());
        try {
            Files.createDirectories(dir);
            try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(dir.resolve(stamp + HTML_SUFFIX)))) {
                out.write(result.body().getBytes(StandardCharsets.UTF_8));
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("listingId", listingId.value());
            meta.put("requestedUri", result.requestedUri().toString());
            meta.put("finalUri", result.finalUri().toString());
            meta.put("status", result.status());
            meta.put("contentType", result.contentType());
            meta.put("fetchedAt", result.fetchedAt().toString());
            meta.put("bodyLength", result.body().length());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(stamp + META_SUFFIX).toFile(), meta);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot archive raw snapshot of " + listingId + " under " + dir, e);
        }
    }

    @Override
    public int purgeExpired(Instant now) {
        return SnapshotRetention.purge(root, name -> name.endsWith(HTML_SUFFIX) || name.endsWith(META_SUFFIX), retention, now);
    }
}
