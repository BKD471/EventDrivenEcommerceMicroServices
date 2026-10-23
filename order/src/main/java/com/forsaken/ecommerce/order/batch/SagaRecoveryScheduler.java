package com.forsaken.ecommerce.order.batch;

import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Launches the saga recovery jobs on a fixed delay. A FAILED or STOPPED previous run is restarted with its
 * original parameters so it resumes where it stopped; otherwise a job is launched only when there is work.
 * Concurrent instances stay safe because each order is claimed before it is processed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order.saga.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SagaRecoveryScheduler {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final IOrderRepository orderRepository;
    private final Job compensationRetryJob;
    private final Job staleOrderRecoveryJob;

    @Scheduled(fixedDelayString = "${order.saga.compensation-retry-delay:30000}")
    public void runCompensationRetry() {
        launch(compensationRetryJob, OrderSagaStatus.COMPENSATION_PENDING);
    }

    @Scheduled(fixedDelayString = "${order.saga.recovery-delay:30000}")
    public void runStaleOrderRecovery() {
        launch(staleOrderRecoveryJob, OrderSagaStatus.RECOVERABLE);
    }

    void launch(final Job job, final Collection<OrderSagaStatus> statuses) {
        try {
            final JobParameters parameters = restartableParameters(job.getName());
            if (parameters != null) {
                log.info("Restarting saga batch job={}", job.getName());
                jobLauncher.run(job, parameters);
                return;
            }
            if (orderRepository.findClaimableIdsBySagaStatus(
                    statuses, LocalDateTime.now(), 0, PageRequest.of(0, 1)).isEmpty()) {
                return;
            }
            jobLauncher.run(job, new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters());
        } catch (Exception ex) {
            log.error("Saga batch job={} could not be launched", job.getName(), ex);
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
