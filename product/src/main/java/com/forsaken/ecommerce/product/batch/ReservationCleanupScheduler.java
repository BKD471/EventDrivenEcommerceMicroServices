package com.forsaken.ecommerce.product.batch;

import com.forsaken.ecommerce.product.model.ReservationStatus;
import com.forsaken.ecommerce.product.repository.IReservationOrderRepository;
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
 * Launches the reservation cleanup jobs on a fixed delay. A FAILED or STOPPED previous run is restarted with its
 * original parameters; otherwise a job is launched only when there is work to do.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "product.reservation.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReservationCleanupScheduler {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final IReservationOrderRepository repository;
    private final ReservationCleanupProperties properties;
    private final Job expiryJob;
    private final Job tombstonePurgeJob;

    public ReservationCleanupScheduler(
            final JobLauncher jobLauncher,
            final JobExplorer jobExplorer,
            final IReservationOrderRepository repository,
            final ReservationCleanupProperties properties,
            @Qualifier("reservationExpiryJob") final Job expiryJob,
            @Qualifier("reservationTombstonePurgeJob") final Job tombstonePurgeJob
    ) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.repository = repository;
        this.properties = properties;
        this.expiryJob = expiryJob;
        this.tombstonePurgeJob = tombstonePurgeJob;
    }

    @Scheduled(fixedDelayString = "${product.reservation.expiry-delay:600000}",
            initialDelayString = "${product.reservation.expiry-delay:600000}")
    public void runExpiry() {
        launch(expiryJob, () -> !repository.findReferencesByStatusCreatedBefore(
                ReservationStatus.RESERVED, Instant.now().minus(properties.expiryAge()), "",
                PageRequest.of(0, 1)).isEmpty());
    }

    @Scheduled(fixedDelayString = "${product.reservation.purge-delay:86400000}",
            initialDelayString = "${product.reservation.purge-delay:86400000}")
    public void runTombstonePurge() {
        launch(tombstonePurgeJob, () -> !repository.findReferencesByStatusReleasedBefore(
                ReservationStatus.RELEASED, Instant.now().minus(properties.tombstoneRetention()), "",
                PageRequest.of(0, 1)).isEmpty());
    }

    void launch(final Job job, final Supplier<Boolean> hasWork) {
        try {
            final JobParameters restart = restartableParameters(job.getName());
            if (restart != null) {
                log.info("Restarting reservation batch job={}", job.getName());
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
            log.error("Reservation batch job={} could not be launched", job.getName(), ex);
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
