package com.forsaken.ecommerce.payment.batch;

import com.forsaken.ecommerce.payment.model.PaymentOutboxEvent.OutboxStatus;
import com.forsaken.ecommerce.payment.repository.IPaymentOutboxRepository;
import com.forsaken.ecommerce.payment.service.PaymentOutboxMaintenanceService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;

/** Chunked, restartable maintenance jobs for the payment outbox. */
@Configuration
public class OutboxMaintenanceBatchConfig {

    public static final String PURGE_JOB = "outboxPurgeJob";
    public static final String REPROCESS_JOB = "outboxReprocessQuarantinedJob";

    @Bean
    public Job outboxPurgeJob(
            final JobRepository jobRepository,
            final PlatformTransactionManager transactionManager,
            final IPaymentOutboxRepository repository,
            final PaymentOutboxMaintenanceService maintenance,
            final OutboxBatchProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final KeysetIdReader reader = new KeysetIdReader((afterId, limit) ->
                repository.findIdsByStatusOlderThan(OutboxStatus.SENT,
                        Instant.now().minus(properties.retention()), afterId, PageRequest.of(0, limit)),
                properties.chunkSize());
        final ItemWriter<Long> writer = chunk -> maintenance.purgeSent(List.copyOf(chunk.getItems()));
        final Step step = new StepBuilder(PURGE_JOB + "Step", jobRepository)
                .<Long, Long>chunk(properties.chunkSize(), transactionManager)
                .reader(reader)
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(properties.retryLimit())
                .listener(new OutboxStepListener(PURGE_JOB, meterRegistry))
                .build();
        return new JobBuilder(PURGE_JOB, jobRepository).start(step).build();
    }

    @Bean
    public Job outboxReprocessQuarantinedJob(
            final JobRepository jobRepository,
            final PlatformTransactionManager transactionManager,
            final IPaymentOutboxRepository repository,
            final PaymentOutboxMaintenanceService maintenance,
            final OutboxBatchProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final KeysetIdReader reader = new KeysetIdReader((afterId, limit) ->
                repository.findIdsByStatus(OutboxStatus.QUARANTINED, afterId, PageRequest.of(0, limit)),
                properties.chunkSize());
        // Events that remain unpublishable are filtered out and stay quarantined.
        final ItemProcessor<Long, Long> processor = id -> maintenance.requeueQuarantined(id) ? id : null;
        final ItemWriter<Long> writer = chunk -> { };
        final Step step = new StepBuilder(REPROCESS_JOB + "Step", jobRepository)
                .<Long, Long>chunk(properties.chunkSize(), transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(properties.retryLimit())
                .skip(RuntimeException.class)
                .skipLimit(properties.skipLimit())
                .listener(new OutboxStepListener(REPROCESS_JOB, meterRegistry))
                .build();
        return new JobBuilder(REPROCESS_JOB, jobRepository).start(step).build();
    }
}
