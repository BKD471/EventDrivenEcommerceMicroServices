package com.forsaken.ecommerce.product.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogSchedulerTest {

    private static final ProductFileStorage.StoredFile FILE =
            new ProductFileStorage.StoredFile("imports/incoming/a.csv", Instant.ofEpochMilli(5));

    private final JobLauncher launcher = mock(JobLauncher.class);
    private final JobExplorer explorer = mock(JobExplorer.class);
    private final ProductFileStorage storage = mock(ProductFileStorage.class);
    private final CatalogBatchProperties properties = new CatalogBatchProperties(
            true, 100, 3, 100, 10, 3,
            "imports/incoming/", "imports/processed/", "imports/failed/", "exports/");
    private final Job exportJob = mock(Job.class);
    private final Job importJob = mock(Job.class);
    private final Job lowStockJob = mock(Job.class);
    private final CatalogScheduler scheduler =
            new CatalogScheduler(launcher, explorer, storage, properties, exportJob, importJob, lowStockJob);

    private JobExecution execution(final BatchStatus status) {
        final JobExecution execution = new JobExecution(new JobInstance(1L, "productImportJob"), 1L,
                new JobParametersBuilder().toJobParameters());
        execution.setStatus(status);
        return execution;
    }

    @Test
    void completedImportMovesFileToProcessed() throws Exception {
        when(launcher.run(eq(importJob), any())).thenReturn(execution(BatchStatus.COMPLETED));

        scheduler.importFile(FILE);

        verify(storage).move("imports/incoming/a.csv", "imports/processed/a.csv");
    }

    @Test
    void alreadyCompletedImportMovesFileToProcessed() throws Exception {
        when(launcher.run(eq(importJob), any())).thenThrow(new JobInstanceAlreadyCompleteException("done"));

        scheduler.importFile(FILE);

        verify(storage).move("imports/incoming/a.csv", "imports/processed/a.csv");
    }

    @Test
    void failedImportStaysForRetryUntilAttemptsAreExhausted() throws Exception {
        final JobExecution failed = execution(BatchStatus.FAILED);
        when(launcher.run(eq(importJob), any())).thenReturn(failed);
        when(explorer.getJobExecutions(failed.getJobInstance())).thenReturn(List.of(failed));

        scheduler.importFile(FILE);

        verify(storage, never()).move(any(), any());
    }

    @Test
    void repeatedlyFailingImportIsMovedToFailed() throws Exception {
        final JobExecution failed = execution(BatchStatus.FAILED);
        when(launcher.run(eq(importJob), any())).thenReturn(failed);
        when(explorer.getJobExecutions(failed.getJobInstance())).thenReturn(List.of(failed, failed, failed));

        scheduler.importFile(FILE);

        verify(storage).move("imports/incoming/a.csv", "imports/failed/a.csv");
    }

    @Test
    void importsOnlyCsvFiles() {
        when(storage.list("imports/incoming/")).thenReturn(List.of(
                new ProductFileStorage.StoredFile("imports/incoming/notes.txt", Instant.EPOCH)));

        scheduler.runImports();

        verify(storage, never()).move(any(), any());
    }

    @Test
    void exportRestartsFailedRunWithItsParameters() throws Exception {
        when(exportJob.getName()).thenReturn("productExportJob");
        final JobInstance instance = new JobInstance(1L, "productExportJob");
        final JobParameters parameters = new JobParametersBuilder().addLong("runAt", 1L).toJobParameters();
        final JobExecution last = new JobExecution(instance, 1L, parameters);
        last.setStatus(BatchStatus.FAILED);
        when(explorer.getLastJobInstance("productExportJob")).thenReturn(instance);
        when(explorer.getLastJobExecution(instance)).thenReturn(last);

        scheduler.runExport();

        verify(launcher).run(exportJob, parameters);
    }

    @Test
    void exportLaunchesNewRunWithFileAndUploadKey() throws Exception {
        when(exportJob.getName()).thenReturn("productExportJob");

        scheduler.runExport();

        verify(launcher).run(eq(exportJob), any(JobParameters.class));
    }
}
