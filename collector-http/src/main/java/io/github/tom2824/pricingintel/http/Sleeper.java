package io.github.tom2824.pricingintel.http;

import java.time.Duration;

/** Abstraction de l'attente, pour que les tests de rate limiting et de retry s'exécutent instantanément. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;

    static Sleeper system() {
        return duration -> Thread.sleep(duration.toMillis());
    }

    static Sleeper noop() {
        return duration -> {
        };
    }
}
