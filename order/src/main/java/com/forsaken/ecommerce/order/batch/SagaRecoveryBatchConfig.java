package com.forsaken.ecommerce.order.batch;

import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import com.forsaken.ecommerce.order.order.service.IOrderSagaRecoveryService;
import com.forsaken.ecommerce.order.order.service.SagaRecoveryException;
import com.forsaken.ecommerce.order.order.service.SagaRecoveryOutcome;
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

import java.util.Collection;

/**
 * Chunked, restartable jobs that replace the former single-tick saga schedulers. Items are order ids; the
 * recovery service does the real work and claims each order, so steps run without a surrounding transaction
 * (cross-service calls must not sit inside a database transaction).
 */
@Configuration
public class SagaRecoveryBatchConfig {

    public static final String COMPENSATION_RETRY_JOB = "compensationRetryJob";
    public static final String STALE_ORDER_RECOVERY_JOB = "staleOrderRecoveryJob";

    @Bean
    public Job compensationRetryJob(
            final JobRepository jobRepository,
            final IOrderRepository orderRepository,
            final IOrderSagaRecoveryService recoveryService,
            final SagaBatchProperties properties,
            final MeterRegistry meterRegistry
    ) {
        return job(COMPENSATION_RETRY_JOB, jobRepository, orderRepository, OrderSagaStatus.COMPENSATION_PENDING,
                recoveryService::retryCompensation, properties, meterRegistry);
    }

    @Bean
    public Job staleOrderRecoveryJob(
            final JobRepository jobRepository,
            final IOrderRepository orderRepository,
            final IOrderSagaRecoveryService recoveryService,
            final SagaBatchProperties properties,
            final MeterRegistry meterRegistry
    ) {
        return job(STALE_ORDER_RECOVERY_JOB, jobRepository, orderRepository, OrderSagaStatus.RECOVERABLE,
                recoveryService::recover, properties, meterRegistry);
    }

    private Job job(
            final String name,
            final JobRepository jobRepository,
            final IOrderRepository orderRepository,
            final Collection<OrderSagaStatus> statuses,
            final ItemProcessor<Integer, SagaRecoveryOutcome> recovery,
            final SagaBatchProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final ItemProcessor<Integer, SagaRecoveryOutcome> processor = orderId -> {
            final SagaRecoveryOutcome outcome = recovery.process(orderId);
            return outcome == SagaRecoveryOutcome.NOT_CLAIMED ? null : outcome;
        };
        final ItemWriter<SagaRecoveryOutcome> writer = chunk -> { };
        final Step step = new StepBuilder(name + "Step", jobRepository)
                .<Integer, SagaRecoveryOutcome>chunk(properties.chunkSize(), new ResourcelessTransactionManager())
                .reader(new ClaimableOrderIdReader(orderRepository, statuses, properties.chunkSize()))
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .processorNonTransactional()
                .retry(TransientDataAccessException.class)
                .retryLimit(properties.retryLimit())
                .skip(SagaRecoveryException.class)
                .skipLimit(properties.skipLimit())
                .listener(new SagaRecoveryStepListener(name, meterRegistry))
                .build();
        return new JobBuilder(name, jobRepository).start(step).build();
    }
}
