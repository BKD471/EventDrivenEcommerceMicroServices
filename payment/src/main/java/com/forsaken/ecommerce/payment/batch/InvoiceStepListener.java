package com.forsaken.ecommerce.payment.batch;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/** Logs a per-run summary and publishes per-run counters for the invoice backfill step. */
@Slf4j
@RequiredArgsConstructor
public class InvoiceStepListener implements StepExecutionListener {

    private final String jobName;
    private final MeterRegistry meterRegistry;

    @Override
    public ExitStatus afterStep(final StepExecution step) {
        record("checked", step.getReadCount());
        record("regenerated", step.getWriteCount());
        record("already_present", step.getFilterCount());
        record("skipped", step.getSkipCount());
        log.info("Invoice batch job={} status={} checked={} regenerated={} alreadyPresent={} skipped={}",
                jobName, step.getStatus(), step.getReadCount(), step.getWriteCount(),
                step.getFilterCount(), step.getSkipCount());
        return step.getExitStatus();
    }

    private void record(final String outcome, final long count) {
        meterRegistry.counter("payment.invoice.batch.items", "job", jobName, "outcome", outcome).increment(count);
    }
}
