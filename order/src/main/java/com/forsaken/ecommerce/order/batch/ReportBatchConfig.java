package com.forsaken.ecommerce.order.batch;

import com.forsaken.ecommerce.order.order.service.OrderReportService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;

/** Restartable daily order sales job: one item per day, each day regenerated in its own transaction. */
@Configuration
public class ReportBatchConfig {

    public static final String DAILY_SUMMARY_JOB = "dailyOrderSalesJob";

    @Bean
    public Job dailyOrderSalesJob(
            final JobRepository jobRepository,
            final PlatformTransactionManager transactionManager,
            final OrderReportService reportService,
            final ReportBatchProperties properties,
            final MeterRegistry meterRegistry
    ) {
        final ItemWriter<LocalDate> writer = chunk -> chunk.getItems().forEach(reportService::reportDay);
        final Step step = new StepBuilder(DAILY_SUMMARY_JOB + "Step", jobRepository)
                .<LocalDate, LocalDate>chunk(properties.chunkSize(), transactionManager)
                .reader(new DayWindowReader(properties.lookbackDays(), LocalDate::now))
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(properties.retryLimit())
                .skip(TransientDataAccessException.class)
                .skipLimit(properties.skipLimit())
                .listener(new ReportStepListener(DAILY_SUMMARY_JOB, meterRegistry))
                .build();
        return new JobBuilder(DAILY_SUMMARY_JOB, jobRepository).start(step).build();
    }
}
