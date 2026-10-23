package com.forsaken.ecommerce.payment.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

import java.net.URI;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceBackfillSchedulerTest {

    private final JobLauncher launcher = mock(JobLauncher.class);
    private final JobExplorer explorer = mock(JobExplorer.class);
    private final Job job = mock(Job.class);

    private InvoiceBackfillScheduler scheduler(final String token) {
        return new InvoiceBackfillScheduler(launcher, explorer, job, new InvoiceBatchProperties(
                true, 50, 3, 100, Duration.ofDays(7), Duration.ofMinutes(15),
                URI.create("http://localhost:8040/api/v1/invoices"), token,
                Duration.ofSeconds(5), Duration.ofSeconds(30)));
    }

    private JobParameters lastRunWas(final BatchStatus status) {
        when(job.getName()).thenReturn("j");
        final JobInstance instance = new JobInstance(1L, "j");
        final JobParameters parameters = new JobParametersBuilder().addLong("runAt", 1L).toJobParameters();
        final JobExecution last = new JobExecution(instance, 1L, parameters);
        last.setStatus(status);
        when(explorer.getLastJobInstance("j")).thenReturn(instance);
        when(explorer.getLastJobExecution(instance)).thenReturn(last);
        return parameters;
    }

    @Test
    void launchesWhenNoPreviousRun() throws Exception {
        when(job.getName()).thenReturn("j");

        scheduler("secret").runBackfill();

        verify(launcher).run(any(), any());
    }

    @Test
    void doesNothingWithoutAToken() throws Exception {
        scheduler("").runBackfill();
        scheduler("").restartInterrupted();

        verify(launcher, never()).run(any(), any());
    }

    @Test
    void restartsFailedRunWithItsParameters() throws Exception {
        final JobParameters parameters = lastRunWas(BatchStatus.FAILED);

        scheduler("secret").restartInterrupted();

        verify(launcher).run(job, parameters);
    }

    @Test
    void doesNotLaunchNewRunWhileInterruptedRunAwaitsRestart() throws Exception {
        lastRunWas(BatchStatus.FAILED);

        scheduler("secret").runBackfill();

        verify(launcher, never()).run(any(), any());
    }
}
