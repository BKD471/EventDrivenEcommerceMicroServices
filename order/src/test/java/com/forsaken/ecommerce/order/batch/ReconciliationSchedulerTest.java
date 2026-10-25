package com.forsaken.ecommerce.order.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliationSchedulerTest {

    private final JobLauncher launcher = mock(JobLauncher.class);
    private final JobExplorer explorer = mock(JobExplorer.class);
    private final Job job = mock(Job.class);
    private final ReconciliationScheduler scheduler =
            new ReconciliationScheduler(launcher, explorer, job, job, job, job, job);

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
    void launchesNewRunWhenNoPreviousRun() throws Exception {
        when(job.getName()).thenReturn("j");

        scheduler.launchNew(job);

        verify(launcher).run(any(), any());
    }

    @Test
    void launchesNewRunAfterCompletedRun() throws Exception {
        lastRunWas(BatchStatus.COMPLETED);

        scheduler.launchNew(job);

        verify(launcher).run(any(), any());
    }

    @Test
    void doesNotStartNewRunWhileAnInterruptedRunAwaitsRestart() throws Exception {
        lastRunWas(BatchStatus.FAILED);

        scheduler.launchNew(job);

        verify(launcher, never()).run(any(), any());
    }

    @Test
    void restartsFailedRunWithItsParameters() throws Exception {
        final JobParameters parameters = lastRunWas(BatchStatus.FAILED);

        scheduler.restart(job);

        verify(launcher).run(job, parameters);
    }

    @Test
    void doesNotRestartCompletedRun() throws Exception {
        lastRunWas(BatchStatus.COMPLETED);

        scheduler.restart(job);

        verify(launcher, never()).run(any(), any());
    }
}
