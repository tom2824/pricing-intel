package io.github.tom2824.pricingintel.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Charge les définitions de sites depuis des fichiers YAML, un site par fichier. Une propriété inconnue
 * fait échouer le chargement : une faute de frappe dans un sélecteur doit se voir tout de suite.
 */
public final class SiteDefinitionLoader {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public SiteDefinition parse(String content) {
        try {
            return yaml.readValue(content, SiteDefinition.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid site definition: " + e.getMessage(), e);
        }
    }

    public SiteDefinition load(Path file) {
        try {
            return yaml.readValue(file.toFile(), SiteDefinition.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid site definition in " + file + ": " + e.getMessage(), e);
        }
    }

    /** Tous les {@code *.yml} / {@code *.yaml} d'un dossier, dans l'ordre alphabétique. Dossier absent : liste vide. */
    public List<SiteDefinition> loadAll(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(SiteDefinitionLoader::isYaml)
                    .sorted()
                    .map(this::load)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list site definitions in " + directory, e);
        }
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return Files.isRegularFile(path) && (name.endsWith(".yml") || name.endsWith(".yaml"));
    }
}
