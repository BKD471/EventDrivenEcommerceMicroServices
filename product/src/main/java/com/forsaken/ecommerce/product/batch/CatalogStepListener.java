package com.forsaken.ecommerce.product.batch;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

import java.time.Duration;

/** Logs a per-run summary and publishes per-run counters for a catalog step. */
@Slf4j
@RequiredArgsConstructor
public class CatalogStepListener implements StepExecutionListener {

    private final String jobName;
    private final MeterRegistry meterRegistry;

    @Override
    public ExitStatus afterStep(final StepExecution step) {
        record("read", step.getReadCount());
        record("written", step.getWriteCount());
        record("skipped", step.getSkipCount());
        final long millis = step.getStartTime() == null || step.getEndTime() == null
                ? 0 : Duration.between(step.getStartTime(), step.getEndTime()).toMillis();
        log.info("Catalog batch job={} status={} read={} written={} skipped={} durationMs={}",
                jobName, step.getStatus(), step.getReadCount(), step.getWriteCount(),
                step.getSkipCount(), millis);
        return step.getExitStatus();
    }

    private void record(final String outcome, final long count) {
        meterRegistry.counter("product.catalog.batch.items", "job", jobName, "outcome", outcome).increment(count);
    }
}
