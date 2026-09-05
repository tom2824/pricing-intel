package io.github.tom2824.pricingintel.collector;

/**
 * Port de sortie : reçoit le bilan d'une collecte (compteurs et échecs). Stocker les échecs permet d'expliquer
 * un trou dans une courbe de prix et de repérer un extracteur cassé sans lire les logs (ADR 0017).
 */
@FunctionalInterface
public interface CollectionReportSink {

    void accept(CollectionReport report);

    static CollectionReportSink none() {
        return report -> {
        };
    }
}
