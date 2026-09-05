package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.collector.CollectionReport;
import io.github.tom2824.pricingintel.collector.CollectionRun;
import io.github.tom2824.pricingintel.collector.PriceSink;
import io.github.tom2824.pricingintel.collector.RawSnapshotStore;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * Lance la collecte au démarrage, purge les archives expirées, journalise le bilan et fixe le code de sortie :
 * 0 si au moins un relevé a été produit, 1 si tout a échoué. Un cron ou GitHub Actions voit ainsi
 * immédiatement une collecte cassée.
 */
@Component
class CollectRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(CollectRunner.class);

    private final CollectionRun collectionRun;
    private final PriceSink sink;
    private final RawSnapshotStore rawStore;
    private final Clock clock;
    private CollectionReport lastReport;

    CollectRunner(CollectionRun collectionRun, PriceSink sink, RawSnapshotStore rawStore, Clock clock) {
        this.collectionRun = collectionRun;
        this.sink = sink;
        this.rawStore = rawStore;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            lastReport = collectionRun.run();
        } finally {
            sink.close();
        }
        LOG.info("Collecte terminée : {}", lastReport.summary());
        for (CollectionReport.Failure failure : lastReport.failures()) {
            LOG.warn("  échec {} [{}]{} : {}", failure.listingId(), failure.sourceId(),
                    failure.retryable() ? " (transitoire)" : "", failure.reason());
        }
        if (lastReport.isTotalFailure()) {
            LOG.error("Aucun relevé collecté sur {} annonce(s)", lastReport.attempted());
        }
        purgeArchives();
    }

    /** Une purge qui échoue ne doit pas transformer une collecte réussie en échec. */
    private void purgeArchives() {
        try {
            int purged = rawStore.purgeExpired(clock.instant());
            if (purged > 0) {
                LOG.info("Archives expirées supprimées : {}", purged);
            }
        } catch (RuntimeException e) {
            LOG.warn("Purge des archives impossible : {}", e.toString());
        }
    }

    Optional<CollectionReport> lastReport() {
        return Optional.ofNullable(lastReport);
    }

    @Override
    public int getExitCode() {
        return lastReport != null && lastReport.isTotalFailure() ? 1 : 0;
    }
}
