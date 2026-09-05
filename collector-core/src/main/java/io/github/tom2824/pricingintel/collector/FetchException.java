package io.github.tom2824.pricingintel.collector;

/** Aucune réponse n'a pu être obtenue pour une URL. */
public class FetchException extends Exception {

    private final boolean retryable;

    public FetchException(String message, boolean retryable) {
        this(message, null, retryable);
    }

    public FetchException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** Vrai pour les erreurs réseau ou de délai ; faux pour une interdiction (robots.txt) ou une URL invalide. */
    public boolean isRetryable() {
        return retryable;
    }
}
