package com.forsaken.ecommerce.order.batch;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/** Logs a per-run summary and publishes per-run counters for a saga recovery step. */
@Slf4j
@RequiredArgsConstructor
public class SagaRecoveryStepListener implements StepExecutionListener {

    private final String jobName;
    private final MeterRegistry meterRegistry;

    @Override
    public ExitStatus afterStep(final StepExecution step) {
        record("read", step.getReadCount());
        record("recovered", step.getWriteCount());
        record("skipped", step.getSkipCount());
        record("not_claimed", step.getFilterCount());
        final long millis = step.getEndTime() == null || step.getStartTime() == null
                ? 0
                : java.time.Duration.between(step.getStartTime(), step.getEndTime()).toMillis();
        log.info("Saga batch job={} status={} read={} recovered={} notClaimed={} skipped={} durationMs={}",
                jobName, step.getStatus(), step.getReadCount(), step.getWriteCount(),
                step.getFilterCount(), step.getSkipCount(), millis);
        return step.getExitStatus();
    }

    private void record(final String outcome, final long count) {
        meterRegistry.counter("order.saga.batch.items", "job", jobName, "outcome", outcome).increment(count);
    }
}
