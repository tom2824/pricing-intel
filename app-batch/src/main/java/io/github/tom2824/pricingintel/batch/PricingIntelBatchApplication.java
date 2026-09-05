package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.persistence.PersistenceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Point d'entrée de la collecte planifiée. Pas de serveur web : l'application démarre, collecte, écrit
 * ses relevés, puis se termine avec un code de sortie exploitable par un cron ou GitHub Actions.
 * C'est le seul module d'application : il assemble les briques, il ne contient aucune logique métier.
 * L'adaptateur PostgreSQL est importé mais ne s'active que sous le profil {@code postgres}.
 */
@SpringBootApplication
@Import(PersistenceConfiguration.class)
public class PricingIntelBatchApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(PricingIntelBatchApplication.class, args)));
    }
}
