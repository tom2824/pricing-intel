package io.github.tom2824.pricingintel.collector;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * Réponse HTTP telle que reçue.
 *
 * @param requestedUri URL demandée
 * @param finalUri     URL finale après redirections
 * @param contentType  en-tête Content-Type, chaîne vide si absent
 */
public record FetchResult(URI requestedUri, URI finalUri, int status, String contentType, String body, Instant fetchedAt) {

    public FetchResult {
        Objects.requireNonNull(requestedUri, "requestedUri");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        finalUri = finalUri == null ? requestedUri : finalUri;
        contentType = contentType == null ? "" : contentType;
        body = body == null ? "" : body;
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    public boolean isRateLimited() {
        return status == 429;
    }

    public boolean isServerError() {
        return status >= 500;
    }
}
