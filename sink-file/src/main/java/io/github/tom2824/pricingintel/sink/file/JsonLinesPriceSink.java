package io.github.tom2824.pricingintel.sink.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.tom2824.pricingintel.collector.PriceSink;
import io.github.tom2824.pricingintel.domain.PriceSnapshot;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Ajoute chaque relevé, en JSON sur une ligne, à la fin d'un fichier {@code .jsonl}. Le fichier est ouvert à la
 * première écriture et flushé à chaque ligne : si la collecte est interrompue, ce qui a été relevé est sur disque.
 */
public final class JsonLinesPriceSink implements PriceSink {

    /** Seuls les composants des records sont écrits : pas les méthodes dérivées comme {@code isDiscounted()}. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final Path file;
    private BufferedWriter writer;

    public JsonLinesPriceSink(Path file) {
        this.file = file;
    }

    public static String toJson(PriceSnapshot snapshot) {
        try {
            return MAPPER.writeValueAsString(snapshot);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot serialize snapshot " + snapshot.listingId(), e);
        }
    }

    @Override
    public synchronized void accept(PriceSnapshot snapshot) {
        try {
            if (writer == null) {
                open();
            }
            writer.write(toJson(snapshot));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write snapshot to " + file, e);
        }
    }

    @Override
    public synchronized void close() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot close " + file, e);
        } finally {
            writer = null;
        }
    }

    private void open() throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
    }
}
