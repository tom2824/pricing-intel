package io.github.tom2824.pricingintel.collector;

/** Une source n'a pas pu produire de relevé pour une annonce. Exception vérifiée : c'est un cas attendu. */
public class ObservationException extends Exception {

    private final boolean retryable;

    public ObservationException(String message, boolean retryable) {
        this(message, null, retryable);
    }

    public ObservationException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** Vrai si l'échec est probablement transitoire (site indisponible, limite de débit). */
    public boolean isRetryable() {
        return retryable;
    }
}
