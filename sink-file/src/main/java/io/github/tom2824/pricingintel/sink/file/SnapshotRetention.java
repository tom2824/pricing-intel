package io.github.tom2824.pricingintel.sink.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Predicate;

/**
 * Nommage et purge des archives : {@code <racine>/<annonce>/<horodatage><suffixe>}, l'horodatage étant
 * {@code yyyyMMdd'T'HHmmss'Z'}. La date d'une archive se lit dans son nom, pas dans ses attributs de fichier,
 * pour survivre à une copie ou à un checkout.
 */
final class SnapshotRetention {

    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final int STAMP_LENGTH = 16;

    private SnapshotRetention() {
    }

    static String stamp(Instant instant) {
        return STAMP.format(instant);
    }

    /** Les identifiants d'annonce sont libres ; on ne garde que ce qui est sûr dans un nom de dossier. */
    static String safeName(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' ? c : '_');
        }
        return sb.toString();
    }

    /**
     * Supprime les fichiers acceptés par {@code fileFilter} dont l'horodatage est antérieur à {@code now - retention},
     * puis les dossiers d'annonce devenus vides.
     *
     * @param retention null pour ne jamais purger
     * @return nombre de fichiers supprimés
     */
    static int purge(Path root, Predicate<String> fileFilter, Duration retention, Instant now) {
        if (retention == null || !Files.isDirectory(root)) {
            return 0;
        }
        Instant cutoff = now.minus(retention);
        int deleted = 0;
        try (DirectoryStream<Path> listings = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path listingDir : listings) {
                deleted += purgeDirectory(listingDir, fileFilter, cutoff);
                try (DirectoryStream<Path> remaining = Files.newDirectoryStream(listingDir)) {
                    if (!remaining.iterator().hasNext()) {
                        Files.delete(listingDir);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot purge snapshots under " + root, e);
        }
        return deleted;
    }

    private static int purgeDirectory(Path dir, Predicate<String> fileFilter, Instant cutoff) throws IOException {
        int deleted = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, Files::isRegularFile)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                Instant stamped = stampOf(name);
                if (stamped != null && stamped.isBefore(cutoff) && fileFilter.test(name)) {
                    Files.delete(file);
                    deleted++;
                }
            }
        }
        return deleted;
    }

    static Instant stampOf(String fileName) {
        if (fileName.length() <= STAMP_LENGTH) {
            return null;
        }
        try {
            return Instant.from(STAMP.parse(fileName.substring(0, STAMP_LENGTH)));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
