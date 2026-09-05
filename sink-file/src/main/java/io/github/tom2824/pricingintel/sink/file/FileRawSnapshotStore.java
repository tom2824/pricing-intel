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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Archive chaque réponse brute sous {@code <racine>/<annonce>/<horodatage>.html.gz}, avec un fichier
 * {@code .meta.json} à côté (statut, URL finale, type de contenu). Séparer "collecter" et "extraire" permet
 * de rejouer une extraction corrigée sur les pages du passé au lieu d'avoir perdu les données.
 */
public final class FileRawSnapshotStore implements RawSnapshotStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final Path root;

    public FileRawSnapshotStore(Path root) {
        this.root = root;
    }

    @Override
    public void store(ListingId listingId, FetchResult result) {
        Path dir = root.resolve(safeName(listingId.value()));
        String stamp = STAMP.format(result.fetchedAt());
        try {
            Files.createDirectories(dir);
            try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(dir.resolve(stamp + ".html.gz")))) {
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
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(stamp + ".meta.json").toFile(), meta);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot archive raw snapshot of " + listingId + " under " + dir, e);
        }
    }

    /** Les identifiants d'annonce sont libres ; on ne garde que ce qui est sûr dans un nom de dossier. */
    static String safeName(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' ? c : '_');
        }
        return sb.toString();
    }
}
