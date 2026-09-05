package io.github.tom2824.pricingintel.batch;

import io.github.tom2824.pricingintel.collector.CollectionReport;
import io.github.tom2824.pricingintel.collector.CollectionReportSink;
import io.github.tom2824.pricingintel.collector.CollectionRun;
import io.github.tom2824.pricingintel.collector.PriceSink;
import io.github.tom2824.pricingintel.collector.RawSnapshotStore;
import io.github.tom2824.pricingintel.persistence.CatalogueImporter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * Au démarrage : importe éventuellement un catalogue, lance la collecte, stocke le bilan, purge les archives
 * expirées et fixe le code de sortie : 0 si au moins un relevé a été produit, 1 si tout a échoué.
 * Un cron ou GitHub Actions voit ainsi immédiatement une collecte cassée.
 */
@Component
class CollectRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(CollectRunner.class);

    private final CollectorProperties properties;
    private final CollectionRun collectionRun;
    private final PriceSink sink;
    private final RawSnapshotStore rawStore;
    private final CollectionReportSink reportSink;
    private final ObjectProvider<CatalogueImporter> catalogueImporter;
    private final Clock clock;
    private CollectionReport lastReport;

    CollectRunner(CollectorProperties properties, CollectionRun collectionRun, PriceSink sink, RawSnapshotStore rawStore,
                  CollectionReportSink reportSink, ObjectProvider<CatalogueImporter> catalogueImporter, Clock clock) {
        this.properties = properties;
        this.collectionRun = collectionRun;
        this.sink = sink;
        this.rawStore = rawStore;
        this.reportSink = reportSink;
        this.catalogueImporter = catalogueImporter;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!importCatalogueIfConfigured()) {
            sink.close();
            return;
        }
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
        storeReport();
        purgeArchives();
    }

    /** @return faux si l'import était demandé sans collecte ensuite */
    private boolean importCatalogueIfConfigured() {
        CollectorProperties.Catalogue catalogue = properties.catalogue();
        if (catalogue.importFile() == null || catalogue.importFile().isBlank()) {
            return true;
        }
        CatalogueImporter importer = catalogueImporter.getIfAvailable(() -> {
            throw new IllegalStateException("collector.catalogue.import-file requires the 'postgres' Spring profile");
        });
        CatalogueImporter.Result result = importer.importFile(Path.of(catalogue.importFile()));
        LOG.info("Catalogue importé depuis {} : {}", catalogue.importFile(), result.summary());
        return catalogue.collectAfterImport();
    }

    /** Un bilan qui ne peut pas être stocké ne doit pas transformer une collecte réussie en échec. */
    private void storeReport() {
        try {
            reportSink.accept(lastReport);
        } catch (RuntimeException e) {
            LOG.warn("Bilan de collecte non stocké : {}", e.toString());
        }
    }

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
