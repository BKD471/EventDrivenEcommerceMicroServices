package com.forsaken.ecommerce.payment.batch;

import com.forsaken.ecommerce.payment.invoice.IInvoiceClient;
import com.forsaken.ecommerce.payment.invoice.InvoiceClientException;
import com.forsaken.ecommerce.payment.model.Payment;
import com.forsaken.ecommerce.payment.repository.IPaymentRepository;
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

import java.time.LocalDateTime;

/**
 * Finds COMPLETED payments without a stored invoice and asks the notification service to regenerate them. Missing
 * invoices are rebuilt under a key derived from the order reference, so reruns never create duplicates.
 */
@Configuration
public class InvoiceBackfillBatchConfig {

    public static final String INVOICE_BACKFILL_JOB = "invoiceBackfillJob";

    @Bean
    public Job invoiceBackfillJob(
            final JobRepository jobRepository,
            final PlatformTransactionManager transactionManager,
            final IPaymentRepository repository,
            final IInvoiceClient invoiceClient,
            final InvoiceBatchProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final KeysetIdReader reader = new KeysetIdReader((afterId, limit) -> {
            final LocalDateTime now = LocalDateTime.now();
            return repository.findCompletedIdsBetween(
                            now.minus(properties.lookback()), now.minus(properties.minAge()),
                            (int) afterId, PageRequest.of(0, limit))
                    .stream().map(Integer::longValue).toList();
        }, properties.chunkSize());
        // Payments that already have an invoice are filtered out.
        final ItemProcessor<Long, Payment> processor = id -> repository.findById(id.intValue())
                .filter(payment -> !invoiceClient.exists(payment.getOrderReference()))
                .orElse(null);
        final ItemWriter<Payment> writer = chunk -> chunk.getItems().forEach(invoiceClient::regenerate);
        final Step step = new StepBuilder(INVOICE_BACKFILL_JOB + "Step", jobRepository)
                .<Long, Payment>chunk(properties.chunkSize(), transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retry(InvoiceClientException.class)
                .retryLimit(properties.retryLimit())
                .skip(InvoiceClientException.class)
                .skipLimit(properties.skipLimit())
                .listener(new InvoiceStepListener(INVOICE_BACKFILL_JOB, meterRegistry))
                .build();
        return new JobBuilder(INVOICE_BACKFILL_JOB, jobRepository).start(step).build();
    }
}
