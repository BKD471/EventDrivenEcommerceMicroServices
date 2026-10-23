package com.forsaken.ecommerce.payment.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Launches the invoice backfill job hourly. A FAILED or STOPPED previous run is resumed with its original
 * parameters by a more frequent check, and blocks a new run until it finishes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.invoice.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InvoiceBackfillScheduler {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job invoiceBackfillJob;
    private final InvoiceBatchProperties properties;

    @Scheduled(cron = "${payment.invoice.cron:0 0 * * * *}")
    public void runBackfill() {
        if (properties.token().isBlank()) {
            log.warn("Invoice backfill skipped: payment.invoice.batch.token is not configured");
            return;
        }
        try {
            if (restartableParameters() != null) {
                return;
            }
            jobLauncher.run(invoiceBackfillJob, new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters());
        } catch (Exception ex) {
            log.error("Invoice job={} could not be launched", invoiceBackfillJob.getName(), ex);
        }
    }

    @Scheduled(
            fixedDelayString = "${payment.invoice.restart-delay:3600000}",
            initialDelayString = "${payment.invoice.restart-delay:3600000}")
    public void restartInterrupted() {
        if (properties.token().isBlank()) {
            return;
        }
        try {
            final JobParameters parameters = restartableParameters();
            if (parameters != null) {
                log.info("Restarting invoice job={}", invoiceBackfillJob.getName());
                jobLauncher.run(invoiceBackfillJob, parameters);
            }
        } catch (Exception ex) {
            log.error("Invoice job={} could not be restarted", invoiceBackfillJob.getName(), ex);
        }
    }

    private JobParameters restartableParameters() {
        final JobInstance instance = jobExplorer.getLastJobInstance(invoiceBackfillJob.getName());
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
