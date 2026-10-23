package com.forsaken.ecommerce.order.batch;

import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaRecoverySchedulerTest {

    private final JobLauncher launcher = mock(JobLauncher.class);
    private final JobExplorer explorer = mock(JobExplorer.class);
    private final IOrderRepository repository = mock(IOrderRepository.class);
    private final Job job = mock(Job.class);
    private final SagaRecoveryScheduler scheduler =
            new SagaRecoveryScheduler(launcher, explorer, repository, job, job);

    @Test
    void shouldNotLaunchWhenNothingIsClaimable() throws Exception {
        when(job.getName()).thenReturn("j");
        when(repository.findClaimableIdsBySagaStatus(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        scheduler.launch(job, OrderSagaStatus.RECOVERABLE);

        verify(launcher, never()).run(any(), any());
    }

    @Test
    void shouldLaunchNewRunWhenWorkExists() throws Exception {
        when(job.getName()).thenReturn("j");
        when(repository.findClaimableIdsBySagaStatus(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(1));

        scheduler.launch(job, OrderSagaStatus.RECOVERABLE);

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

        scheduler.launch(job, OrderSagaStatus.RECOVERABLE);

        verify(launcher).run(job, parameters);
        verify(repository, never()).findClaimableIdsBySagaStatus(any(), any(), any(), any());
    }
}
