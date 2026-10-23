package com.forsaken.ecommerce.product.batch;

import com.forsaken.ecommerce.product.model.ReservationStatus;
import com.forsaken.ecommerce.product.repository.IReservationOrderRepository;
import com.forsaken.ecommerce.product.service.ReservationCleanupService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.function.Predicate;

/**
 * Chunked, restartable jobs that keep inventory reservations bounded. Items are order references; the cleanup
 * service does the real work in its own transactions, so steps run without a surrounding one (the expiry check
 * calls the order service and must not sit inside a database transaction).
 */
@Configuration
public class ReservationCleanupBatchConfig {

    public static final String EXPIRY_JOB = "reservationExpiryJob";
    public static final String TOMBSTONE_PURGE_JOB = "reservationTombstonePurgeJob";

    @Bean
    public Job reservationExpiryJob(
            final JobRepository jobRepository,
            final IReservationOrderRepository repository,
            final ReservationCleanupService cleanup,
            final ReservationCleanupProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final KeysetReferenceReader reader = new KeysetReferenceReader((after, limit) ->
                repository.findReferencesByStatusCreatedBefore(ReservationStatus.RESERVED,
                        Instant.now().minus(properties.expiryAge()), after, PageRequest.of(0, limit)),
                properties.chunkSize());
        return job(EXPIRY_JOB, jobRepository, reader, cleanup::expireIfOrderIsDead, properties, meterRegistry);
    }

    @Bean
    public Job reservationTombstonePurgeJob(
            final JobRepository jobRepository,
            final IReservationOrderRepository repository,
            final ReservationCleanupService cleanup,
            final ReservationCleanupProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final KeysetReferenceReader reader = new KeysetReferenceReader((after, limit) ->
                repository.findReferencesByStatusReleasedBefore(ReservationStatus.RELEASED,
                        Instant.now().minus(properties.tombstoneRetention()), after, PageRequest.of(0, limit)),
                properties.chunkSize());
        return job(TOMBSTONE_PURGE_JOB, jobRepository, reader,
                reference -> cleanup.purgeTombstone(reference, Instant.now().minus(properties.tombstoneRetention())),
                properties, meterRegistry);
    }

    private Job job(
            final String name,
            final JobRepository jobRepository,
            final KeysetReferenceReader reader,
            final Predicate<String> action,
            final ReservationCleanupProperties properties,
            final MeterRegistry meterRegistry
    ) {
        // Items the action leaves untouched are filtered, so counts distinguish "changed" from "kept".
        final ItemProcessor<String, String> processor = reference -> action.test(reference) ? reference : null;
        final ItemWriter<String> writer = chunk -> { };
        final Step step = new StepBuilder(name + "Step", jobRepository)
                .<String, String>chunk(properties.chunkSize(), new ResourcelessTransactionManager())
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .processorNonTransactional()
                .retry(TransientDataAccessException.class)
                .retryLimit(properties.retryLimit())
                .skip(RuntimeException.class)
                .skipLimit(properties.skipLimit())
                .listener(new ReservationStepListener(name, meterRegistry))
                .build();
        return new JobBuilder(name, jobRepository).start(step).build();
    }
}
