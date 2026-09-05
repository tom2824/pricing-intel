package io.github.tom2824.pricingintel.persistence;

import io.github.tom2824.pricingintel.collector.CollectionReport;
import io.github.tom2824.pricingintel.collector.CollectionReportSink;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** Stocke chaque exécution de collecte et ses échecs, pour expliquer les trous des courbes (ADR 0017). */
public class PostgresCollectionReportSink implements CollectionReportSink {

    private final JdbcClient jdbc;

    public PostgresCollectionReportSink(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void accept(CollectionReport report) {
        long runId = jdbc.sql("""
                        insert into collection_run (started_at, finished_at, attempted, collected, failed)
                        values (:started_at, :finished_at, :attempted, :collected, :failed)
                        returning id
                        """)
                .param("started_at", report.startedAt().atOffset(ZoneOffset.UTC))
                .param("finished_at", report.finishedAt().atOffset(ZoneOffset.UTC))
                .param("attempted", report.attempted())
                .param("collected", report.collected())
                .param("failed", report.failures().size())
                .query(Long.class).single();

        for (CollectionReport.Failure failure : report.failures()) {
            jdbc.sql("""
                            insert into collection_failure (run_id, listing_id, listing_code, source_id, reason, retryable, occurred_at)
                            values (:run_id, (select id from listing where code = :listing_code), :listing_code, :source_id,
                                    :reason, :retryable, :occurred_at)
                            """)
                    .param("run_id", runId)
                    .param("listing_code", failure.listingId().value())
                    .param("source_id", failure.sourceId())
                    .param("reason", failure.reason())
                    .param("retryable", failure.retryable())
                    .param("occurred_at", report.finishedAt().atOffset(ZoneOffset.UTC))
                    .update();
        }
    }
}
