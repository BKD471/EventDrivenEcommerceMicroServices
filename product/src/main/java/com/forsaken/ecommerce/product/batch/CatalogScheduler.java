package com.forsaken.ecommerce.product.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Launches the catalog jobs. Export and low-stock run on cron; imports poll the incoming prefix. An interrupted
 * (FAILED or STOPPED) run is resumed with its original parameters before anything new is started. A file is moved
 * to the processed prefix once its import completes, and to the failed prefix after repeated failures.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "product.catalog.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CatalogScheduler {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final ProductFileStorage storage;
    private final CatalogBatchProperties properties;
    private final Job exportJob;
    private final Job importJob;
    private final Job lowStockJob;

    public CatalogScheduler(
            final JobLauncher jobLauncher,
            final JobExplorer jobExplorer,
            final ProductFileStorage storage,
            final CatalogBatchProperties properties,
            @Qualifier("productExportJob") final Job exportJob,
            @Qualifier("productImportJob") final Job importJob,
            @Qualifier("lowStockAlertJob") final Job lowStockJob
    ) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.storage = storage;
        this.properties = properties;
        this.exportJob = exportJob;
        this.importJob = importJob;
        this.lowStockJob = lowStockJob;
    }

    @Scheduled(cron = "${product.catalog.export-cron:0 0 3 * * *}")
    public void runExport() {
        try {
            final JobParameters restart = restartableParameters(exportJob.getName());
            if (restart != null) {
                log.info("Restarting catalog batch job={}", exportJob.getName());
                jobLauncher.run(exportJob, restart);
                return;
            }
            final long runAt = System.currentTimeMillis();
            final String name = "products-" + Instant.ofEpochMilli(runAt).toString().replace(':', '-') + ".csv";
            jobLauncher.run(exportJob, new JobParametersBuilder()
                    .addLong("runAt", runAt)
                    .addString(CatalogBatchConfig.PARAM_FILE,
                            Path.of(System.getProperty("java.io.tmpdir"), name).toString())
                    .addString(CatalogBatchConfig.PARAM_UPLOAD_KEY, properties.exportPrefix() + name)
                    .toJobParameters());
        } catch (Exception ex) {
            log.error("Catalog batch job={} could not be launched", exportJob.getName(), ex);
        }
    }

    @Scheduled(cron = "${product.catalog.low-stock-cron:0 0 8 * * *}")
    public void runLowStock() {
        try {
            final JobParameters restart = restartableParameters(lowStockJob.getName());
            jobLauncher.run(lowStockJob, restart != null ? restart : new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters());
        } catch (Exception ex) {
            log.error("Catalog batch job={} could not be launched", lowStockJob.getName(), ex);
        }
    }

    @Scheduled(fixedDelayString = "${product.catalog.import-delay:900000}",
            initialDelayString = "${product.catalog.import-delay:900000}")
    public void runImports() {
        final List<ProductFileStorage.StoredFile> files;
        try {
            files = storage.list(properties.importPrefix()).stream()
                    .filter(file -> file.key().endsWith(".csv"))
                    .toList();
        } catch (Exception ex) {
            log.error("Catalog import could not list {}", properties.importPrefix(), ex);
            return;
        }
        files.forEach(this::importFile);
    }

    void importFile(final ProductFileStorage.StoredFile file) {
        final JobParameters parameters = new JobParametersBuilder()
                .addString(CatalogBatchConfig.PARAM_KEY, file.key())
                .addLong("lastModified", file.lastModified().toEpochMilli())
                .toJobParameters();
        try {
            final JobExecution execution = jobLauncher.run(importJob, parameters);
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                moveTo(file.key(), properties.processedPrefix());
            } else if (attempts(execution.getJobInstance()) >= properties.maxImportAttempts()) {
                log.error("Catalog import of {} failed {} times; moving it aside", file.key(),
                        properties.maxImportAttempts());
                moveTo(file.key(), properties.failedPrefix());
            }
        } catch (JobInstanceAlreadyCompleteException ex) {
            moveTo(file.key(), properties.processedPrefix());
        } catch (Exception ex) {
            log.error("Catalog import of {} could not be launched", file.key(), ex);
        }
    }

    private int attempts(final JobInstance instance) {
        return jobExplorer.getJobExecutions(instance).size();
    }

    private void moveTo(final String key, final String prefix) {
        try {
            storage.move(key, prefix + key.substring(key.lastIndexOf('/') + 1));
        } catch (Exception ex) {
            log.error("Catalog import file {} could not be moved to {}", key, prefix, ex);
        }
    }

    private JobParameters restartableParameters(final String jobName) {
        final JobInstance instance = jobExplorer.getLastJobInstance(jobName);
        if (instance == null) {
            return null;
        }
        final JobExecution last = jobExplorer.getLastJobExecution(instance);
        if (last == null || (last.getStatus() != BatchStatus.FAILED && last.getStatus() != BatchStatus.STOPPED)) {
            return null;
        }
        return last.getJobParameters();
    }
}
