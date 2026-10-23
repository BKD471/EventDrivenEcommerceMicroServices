package com.forsaken.ecommerce.common.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

/** Runs {@link BatchMetadataPurger} on a cron. Failures are logged and retried on the next run. */
public class BatchMetadataPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchMetadataPurgeScheduler.class);

    private final BatchMetadataPurger purger;
    private final Duration retention;

    public BatchMetadataPurgeScheduler(final BatchMetadataPurger purger, final Duration retention) {
        this.purger = purger;
        this.retention = retention;
    }

    @Scheduled(cron = "${batch.metadata.purge.cron:0 30 4 * * *}")
    public void purge() {
        try {
            final int deleted = purger.purgeOlderThan(retention);
            log.info("Batch metadata purge removed {} job executions older than {}", deleted, retention);
        } catch (RuntimeException ex) {
            log.error("Batch metadata purge failed", ex);
        }
    }
}
