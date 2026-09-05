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
        @DefaultValue Raw raw) {

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

    public record Listings(@NotBlank @DefaultValue("config/listings.yml") String file) {
    }

    public enum SinkType { CONSOLE, JSONL }

    public record Sinks(
            @NotEmpty @DefaultValue("console") List<SinkType> types,
            @NotBlank @DefaultValue("data/releves.jsonl") String jsonlFile) {
    }

    public record Raw(
            @DefaultValue("true") boolean enabled,
            @NotBlank @DefaultValue("data/raw") String dir) {
    }
}
