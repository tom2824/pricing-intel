package io.github.tom2824.pricingintel.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée de la collecte planifiée. Pas de serveur web : l'application démarre, collecte, écrit
 * ses relevés, puis se termine avec un code de sortie exploitable par un cron ou GitHub Actions.
 * C'est le seul module qui connaît Spring : il assemble les briques, il ne contient aucune logique métier.
 */
@SpringBootApplication
public class PricingIntelBatchApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(PricingIntelBatchApplication.class, args)));
    }
}
