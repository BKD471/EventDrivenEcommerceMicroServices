package com.forsaken.ecommerce.payment.batch;

import com.forsaken.ecommerce.payment.model.PaymentOutboxEvent.OutboxStatus;
import com.forsaken.ecommerce.payment.repository.IPaymentOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Launches the outbox maintenance jobs on a fixed delay. A FAILED or STOPPED previous run is restarted with its
 * original parameters; otherwise a job is launched only when there is work to do.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "payment.outbox.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxMaintenanceScheduler {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final IPaymentOutboxRepository repository;
    private final OutboxBatchProperties properties;
    private final Job purgeJob;
    private final Job reprocessJob;

    public OutboxMaintenanceScheduler(
            final JobLauncher jobLauncher,
            final JobExplorer jobExplorer,
            final IPaymentOutboxRepository repository,
            final OutboxBatchProperties properties,
            @Qualifier("outboxPurgeJob") final Job purgeJob,
            @Qualifier("outboxReprocessQuarantinedJob") final Job reprocessJob
    ) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.repository = repository;
        this.properties = properties;
        this.purgeJob = purgeJob;
        this.reprocessJob = reprocessJob;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.purge-delay:3600000}",
            initialDelayString = "${payment.outbox.purge-delay:3600000}")
    public void runPurge() {
        launch(purgeJob, () -> !repository.findIdsByStatusOlderThan(
                OutboxStatus.SENT, Instant.now().minus(properties.retention()), 0L, PageRequest.of(0, 1)).isEmpty());
    }

    @Scheduled(fixedDelayString = "${payment.outbox.reprocess-delay:3600000}",
            initialDelayString = "${payment.outbox.reprocess-delay:3600000}")
    public void runReprocess() {
        launch(reprocessJob, () -> !repository.findIdsByStatus(
                OutboxStatus.QUARANTINED, 0L, PageRequest.of(0, 1)).isEmpty());
    }

    void launch(final Job job, final Supplier<Boolean> hasWork) {
        try {
            final JobParameters restart = restartableParameters(job.getName());
            if (restart != null) {
                log.info("Restarting outbox batch job={}", job.getName());
                jobLauncher.run(job, restart);
                return;
            }
            if (!hasWork.get()) {
                return;
            }
            jobLauncher.run(job, new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters());
        } catch (Exception ex) {
            log.error("Outbox batch job={} could not be launched", job.getName(), ex);
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
