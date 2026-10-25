package com.forsaken.ecommerce.order.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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

import java.util.List;

/**
 * Launches the reconciliation jobs nightly. A FAILED or STOPPED previous run is restarted with its original
 * parameters on a separate, more frequent check so it resumes where it stopped.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "order.reconciliation.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationScheduler {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final List<Job> reconciliationJobs;

    public ReconciliationScheduler(
            final JobLauncher jobLauncher,
            final JobExplorer jobExplorer,
            @Qualifier(ReconciliationBatchConfig.ORDER_PAYMENT_JOB) final Job orderPaymentReconciliationJob,
            @Qualifier(ReconciliationBatchConfig.PAYMENT_ORDER_JOB) final Job paymentOrderReconciliationJob,
            @Qualifier(ReconciliationBatchConfig.COMPLETED_ORDER_INVENTORY_JOB)
            final Job completedOrderInventoryReconciliationJob,
            @Qualifier(ReconciliationBatchConfig.FAILED_ORDER_INVENTORY_JOB)
            final Job failedOrderInventoryReconciliationJob,
            @Qualifier(ReconciliationBatchConfig.ORPHAN_RESERVATION_JOB) final Job orphanReservationReconciliationJob
    ) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.reconciliationJobs = List.of(
                orderPaymentReconciliationJob,
                paymentOrderReconciliationJob,
                completedOrderInventoryReconciliationJob,
                failedOrderInventoryReconciliationJob,
                orphanReservationReconciliationJob);
    }

    @Scheduled(cron = "${order.reconciliation.cron:0 0 2 * * *}")
    public void runNightly() {
        reconciliationJobs.forEach(this::launchNew);
    }

    @Scheduled(
            fixedDelayString = "${order.reconciliation.restart-delay:3600000}",
            initialDelayString = "${order.reconciliation.restart-delay:3600000}")
    public void restartInterrupted() {
        reconciliationJobs.forEach(this::restart);
    }

    void launchNew(final Job job) {
        try {
            if (restartableParameters(job.getName()) != null) {
                return;
            }
            jobLauncher.run(job, new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters());
        } catch (Exception ex) {
            log.error("Reconciliation job={} could not be launched", job.getName(), ex);
        }
    }

    void restart(final Job job) {
        try {
            final JobParameters parameters = restartableParameters(job.getName());
            if (parameters != null) {
                log.info("Restarting reconciliation job={}", job.getName());
                jobLauncher.run(job, parameters);
            }
        } catch (Exception ex) {
            log.error("Reconciliation job={} could not be restarted", job.getName(), ex);
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
