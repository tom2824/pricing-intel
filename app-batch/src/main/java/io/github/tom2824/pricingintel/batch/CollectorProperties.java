package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.http.FetcherConfig;
import io.github.tom2824.pricingintel.http.ProxyPolicy;
import io.github.tom2824.pricingintel.http.RetryPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Toute la configuration de la collecte, sous le préfixe {@code collector}. Chaque valeur se surcharge en
 * ligne de commande ({@code --collector.sinks.types=jsonl}) ou par variable d'environnement
 * ({@code COLLECTOR_PROXY_MODE=fixed}), ce qui permet de lancer chaque brique indépendamment sans toucher au code.
 */
@Validated
@ConfigurationProperties(prefix = "collector")
public record CollectorProperties(
        @NotBlank @DefaultValue(FetcherConfig.DEFAULT_USER_AGENT) String userAgent,
        @DefaultValue("20s") Duration timeout,
        @DefaultValue("true") boolean respectRobotsTxt,
        @DefaultValue("3s") Duration minIntervalPerHost,
        @NotBlank @DefaultValue("EUR") String defaultCurrency,
        @DefaultValue Proxy proxy,
        @DefaultValue Retry retry,
        @DefaultValue Sites sites,
        @DefaultValue Listings listings,
        @DefaultValue Sinks sinks,
        @DefaultValue Raw raw,
        @DefaultValue Catalogue catalogue) {

    public FetcherConfig toFetcherConfig() {
        return new FetcherConfig(userAgent, timeout, proxy.toPolicy(), minIntervalPerHost, retry.toPolicy(), respectRobotsTxt);
    }

    public enum ProxyMode { NONE, FIXED, ROTATING }

    /**
     * @param servers pour {@code rotating} : liste d'adresses {@code host:port}
     */
    public record Proxy(
            @DefaultValue("none") ProxyMode mode,
            String host,
            @DefaultValue("0") int port,
            @DefaultValue List<String> servers) {

        public ProxyPolicy toPolicy() {
            return switch (mode) {
                case NONE -> ProxyPolicy.none();
                case FIXED -> ProxyPolicy.fixed(host, port);
                case ROTATING -> ProxyPolicy.rotating(servers);
            };
        }
    }

    public record Retry(
            @Min(1) @DefaultValue("3") int maxAttempts,
            @DefaultValue("2s") Duration initialBackoff,
            @DefaultValue("2.0") double multiplier,
            @DefaultValue("30s") Duration maxBackoff) {

        public RetryPolicy toPolicy() {
            return new RetryPolicy(maxAttempts, initialBackoff, multiplier, maxBackoff);
        }
    }

    /**
     * @param dir               dossier des définitions YAML, un site par fichier
     * @param allowUnknownHosts si vrai, un hôte sans définition est tenté avec le seul extracteur JSON-LD
     */
    public record Sites(
            @NotBlank @DefaultValue("config/sites") String dir,
            @DefaultValue("true") boolean allowUnknownHosts) {
    }

    public enum ListingsSource { YAML, DATABASE }

    /** D'où viennent les annonces à relever : le fichier YAML, ou la base (profil {@code postgres}). */
    public record Listings(
            @DefaultValue("yaml") ListingsSource source,
            @NotBlank @DefaultValue("config/listings.yml") String file) {
    }

    /**
     * Import d'un catalogue YAML (produits, identifiants, annonces) avant la collecte, profil {@code postgres}.
     *
     * @param importFile         chemin du fichier ; rien n'est importé s'il est absent
     * @param collectAfterImport faux pour importer puis s'arrêter
     */
    public record Catalogue(String importFile, @DefaultValue("true") boolean collectAfterImport) {
    }

    public enum SinkType { CONSOLE, JSONL, POSTGRES }

    public record Sinks(
            @NotEmpty @DefaultValue("console") List<SinkType> types,
            @NotBlank @DefaultValue("data/releves.jsonl") String jsonlFile) {
    }

    /**
     * Archivage des pages (ADR 0007 et 0014). Deux formats cumulables sous le même dossier :
     * la version distillée (légère, longue rétention) et le HTML complet (lourd, courte rétention, débogage).
     * Une rétention absente ou nulle signifie « ne jamais purger ».
     */
    public record Raw(
            @DefaultValue("true") boolean enabled,
            @NotBlank @DefaultValue("data/raw") String dir,
            @DefaultValue Distilled distilled,
            @DefaultValue Html html) {

        public record Distilled(@DefaultValue("true") boolean enabled, @DefaultValue("180d") Duration retention) {
        }

        public record Html(@DefaultValue("false") boolean enabled, @DefaultValue("7d") Duration retention) {
        }
    }
}
