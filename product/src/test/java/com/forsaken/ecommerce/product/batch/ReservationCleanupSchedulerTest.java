package com.forsaken.ecommerce.product.batch;

import com.forsaken.ecommerce.product.repository.IReservationOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationCleanupSchedulerTest {

    private final JobLauncher launcher = mock(JobLauncher.class);
    private final JobExplorer explorer = mock(JobExplorer.class);
    private final Job job = mock(Job.class);
    private final ReservationCleanupScheduler scheduler = new ReservationCleanupScheduler(
            launcher, explorer, mock(IReservationOrderRepository.class),
            new ReservationCleanupProperties(true, 10, 3, 5, Duration.ofHours(1), Duration.ofDays(30)), job, job);

    @Test
    void shouldNotLaunchWhenThereIsNoWork() throws Exception {
        when(job.getName()).thenReturn("j");

        scheduler.launch(job, () -> false);

        verify(launcher, never()).run(any(), any());
    }

    @Test
    void shouldLaunchNewRunWhenWorkExists() throws Exception {
        when(job.getName()).thenReturn("j");

        scheduler.launch(job, () -> true);

        verify(launcher).run(any(), any());
    }

    @Test
    void shouldRestartFailedRunWithItsParameters() throws Exception {
        when(job.getName()).thenReturn("j");
        final JobInstance instance = new JobInstance(1L, "j");
        final JobParameters parameters = new JobParametersBuilder().addLong("runAt", 1L).toJobParameters();
        final JobExecution failed = new JobExecution(instance, 1L, parameters);
        failed.setStatus(BatchStatus.FAILED);
        when(explorer.getLastJobInstance("j")).thenReturn(instance);
        when(explorer.getLastJobExecution(instance)).thenReturn(failed);

        scheduler.launch(job, () -> false);

        verify(launcher).run(job, parameters);
    }
}
