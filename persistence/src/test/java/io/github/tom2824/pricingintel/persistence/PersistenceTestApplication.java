package io.github.tom2824.pricingintel.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** Application minimale pour tester l'adaptateur avec un vrai PostgreSQL embarqué et les vraies migrations. */
@SpringBootApplication
public class PersistenceTestApplication {

    public static final Instant NOW = Instant.parse("2026-09-05T08:00:00Z");

    @Bean
    Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
