package com.forsaken.ecommerce.product.batch;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes old catalog files from S3: nightly exports, imports that were processed and imports that failed. Each
 * prefix has its own retention; failed files are kept longest because someone has to look at them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "product.catalog.purge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CatalogFilePurgeScheduler {

    private final ProductFileStorage storage;
    private final CatalogBatchProperties batchProperties;
    private final CatalogFilePurgeProperties purgeProperties;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${product.catalog.purge-cron:0 0 5 * * *}")
    public void purge() {
        purgePrefix(batchProperties.exportPrefix(), purgeProperties.exportRetention());
        purgePrefix(batchProperties.processedPrefix(), purgeProperties.processedRetention());
        purgePrefix(batchProperties.failedPrefix(), purgeProperties.failedRetention());
    }

    int purgePrefix(final String prefix, final Duration retention) {
        int deleted = 0;
        try {
            final Instant cutoff = Instant.now().minus(retention);
            for (final ProductFileStorage.StoredFile file : storage.list(prefix)) {
                if (!file.lastModified().isBefore(cutoff)) {
                    continue;
                }
                try {
                    storage.delete(file.key());
                    deleted++;
                } catch (RuntimeException ex) {
                    log.warn("Could not delete catalog file key={}", file.key(), ex);
                }
            }
        } catch (RuntimeException ex) {
            log.error("Catalog file purge failed for prefix={}", prefix, ex);
        }
        meterRegistry.counter("product.catalog.files.purged", "prefix", prefix).increment(deleted);
        log.info("Catalog file purge prefix={} retention={} deleted={}", prefix, retention, deleted);
        return deleted;
    }
}
