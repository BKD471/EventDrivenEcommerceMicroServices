package com.forsaken.ecommerce.order.batch;

import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import com.forsaken.ecommerce.order.order.service.OrderReconciliationService;
import com.forsaken.ecommerce.order.order.service.ReconciliationFinding;
import com.forsaken.ecommerce.order.payment.IPaymentService;
import com.forsaken.ecommerce.order.payment.PaymentLookupException;
import com.forsaken.ecommerce.order.payment.PaymentReconciliationItem;
import com.forsaken.ecommerce.order.product.ProductLookupException;
import com.forsaken.ecommerce.order.product.ProductReconciliationClient;
import com.forsaken.ecommerce.order.product.ProductReservationReconciliationItem;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.Instant;

/**
 * Nightly, read-only consistency jobs across the order, payment, and product services. Inconsistencies are written
 * as findings (logged and counted), consistent items are filtered.
 * Steps run without a surrounding transaction because they call another service.
 */
@Slf4j
@Configuration
public class ReconciliationBatchConfig {

    public static final String ORDER_PAYMENT_JOB = "orderPaymentReconciliationJob";
    public static final String PAYMENT_ORDER_JOB = "paymentOrderReconciliationJob";
    public static final String COMPLETED_ORDER_INVENTORY_JOB = "completedOrderInventoryReconciliationJob";
    public static final String FAILED_ORDER_INVENTORY_JOB = "failedOrderInventoryReconciliationJob";
    public static final String ORPHAN_RESERVATION_JOB = "orphanReservationReconciliationJob";

    private static final String AUTHORIZED = "AUTHORIZED";

    /** COMPLETED orders that lack a COMPLETED payment. */
    @Bean
    public Job orderPaymentReconciliationJob(
            final JobRepository jobRepository,
            final IOrderRepository orderRepository,
            final OrderReconciliationService service,
            final ReconciliationProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final ItemReader<Integer> reader = new KeysetReader<>(
                "reconciliation.order.last.read.id",
                (afterId, size) -> orderRepository.findIdsBySagaStatusCreatedBefore(
                        OrderSagaStatus.COMPLETED, LocalDateTime.now().minus(properties.minAge()),
                        afterId, PageRequest.of(0, size)),
                Integer::intValue,
                properties.chunkSize());
        return job(ORDER_PAYMENT_JOB, jobRepository, reader,
                orderId -> service.checkCompletedOrder(orderId).orElse(null), properties, meterRegistry);
    }

    /** AUTHORIZED payments whose order is missing or FAILED. */
    @Bean
    public Job paymentOrderReconciliationJob(
            final JobRepository jobRepository,
            final IPaymentService paymentService,
            final OrderReconciliationService service,
            final ReconciliationProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final ItemReader<PaymentReconciliationItem> reader = new KeysetReader<>(
                "reconciliation.payment.last.read.id",
                (afterId, size) -> paymentService.findByStatusCreatedBefore(
                        AUTHORIZED, LocalDateTime.now().minus(properties.minAge()), afterId, size),
                PaymentReconciliationItem::id,
                properties.chunkSize());
        return job(PAYMENT_ORDER_JOB, jobRepository, reader,
                payment -> service.checkAuthorizedPayment(payment).orElse(null), properties, meterRegistry);
    }

    /** COMPLETED orders that do not have a confirmed inventory reservation. */
    @Bean
    public Job completedOrderInventoryReconciliationJob(
            final JobRepository jobRepository,
            final IOrderRepository orderRepository,
            final OrderReconciliationService service,
            final ReconciliationProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final ItemReader<Integer> reader = new KeysetReader<>(
                "reconciliation.completed.inventory.last.read.id",
                (afterId, size) -> orderRepository.findIdsBySagaStatusCreatedBefore(
                        OrderSagaStatus.COMPLETED, LocalDateTime.now().minus(properties.minAge()),
                        afterId, PageRequest.of(0, size)),
                Integer::intValue,
                properties.chunkSize());
        return job(COMPLETED_ORDER_INVENTORY_JOB, jobRepository, reader,
                orderId -> service.checkCompletedOrderReservation(orderId).orElse(null), properties, meterRegistry);
    }

    /** FAILED orders that still retain an active inventory reservation. */
    @Bean
    public Job failedOrderInventoryReconciliationJob(
            final JobRepository jobRepository,
            final IOrderRepository orderRepository,
            final OrderReconciliationService service,
            final ReconciliationProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final ItemReader<Integer> reader = new KeysetReader<>(
                "reconciliation.failed.inventory.last.read.id",
                (afterId, size) -> orderRepository.findIdsBySagaStatusCreatedBefore(
                        OrderSagaStatus.FAILED, LocalDateTime.now().minus(properties.minAge()),
                        afterId, PageRequest.of(0, size)),
                Integer::intValue,
                properties.chunkSize());
        return job(FAILED_ORDER_INVENTORY_JOB, jobRepository, reader,
                orderId -> service.checkFailedOrderReservation(orderId).orElse(null), properties, meterRegistry);
    }

    /** Old active product reservations with no matching order. */
    @Bean
    public Job orphanReservationReconciliationJob(
            final JobRepository jobRepository,
            final ProductReconciliationClient productClient,
            final OrderReconciliationService service,
            final ReconciliationProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final ItemReader<ProductReservationReconciliationItem> reader = new StringKeysetReader<>(
                "reconciliation.orphan.reservation.last.read.reference",
                (afterReference, size) -> productClient.findReservedCreatedBefore(
                        Instant.now().minus(properties.minAge()), afterReference, size),
                ProductReservationReconciliationItem::orderReference,
                properties.chunkSize());
        return job(ORPHAN_RESERVATION_JOB, jobRepository, reader,
                reservation -> service.checkReservationOrder(reservation).orElse(null), properties, meterRegistry);
    }

    private <T> Job job(
            final String name,
            final JobRepository jobRepository,
            final ItemReader<T> reader,
            final ItemProcessor<T, ReconciliationFinding> processor,
            final ReconciliationProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final ItemWriter<ReconciliationFinding> writer = chunk -> chunk.getItems().forEach(finding -> {
            log.warn("Reconciliation job={} finding={} orderId={} paymentId={} detail={}",
                    name, finding.type(), finding.orderId(), finding.paymentId(), finding.detail());
            meterRegistry.counter("order.reconciliation.findings", "type", finding.type().name()).increment();
        });
        final Step step = new StepBuilder(name + "Step", jobRepository)
                .<T, ReconciliationFinding>chunk(properties.chunkSize(), new ResourcelessTransactionManager())
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .processorNonTransactional()
                .retry(TransientDataAccessException.class)
                .retry(PaymentLookupException.class)
                .retry(ProductLookupException.class)
                .retryLimit(properties.retryLimit())
                .skip(PaymentLookupException.class)
                .skip(ProductLookupException.class)
                .skip(TransientDataAccessException.class)
                .skipLimit(properties.skipLimit())
                .listener(new ReconciliationStepListener(name, meterRegistry))
                .build();
        return new JobBuilder(name, jobRepository).start(step).build();
    }
}
