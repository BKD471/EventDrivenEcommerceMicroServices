package com.forsaken.ecommerce.order.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Launches the daily summary job after midnight. A FAILED or STOPPED previous run is resumed with its original
 * parameters by a more frequent check, and blocks a new run until it finishes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order.report.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReportScheduler {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job dailyOrderSalesJob;

    @Scheduled(cron = "${order.report.cron:0 30 0 * * *}")
    public void runDaily() {
        try {
            if (restartableParameters() != null) {
                return;
            }
            jobLauncher.run(dailyOrderSalesJob, new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters());
        } catch (Exception ex) {
            log.error("Report job={} could not be launched", dailyOrderSalesJob.getName(), ex);
        }
    }

    @Scheduled(
            fixedDelayString = "${order.report.restart-delay:3600000}",
            initialDelayString = "${order.report.restart-delay:3600000}")
    public void restartInterrupted() {
        try {
            final JobParameters parameters = restartableParameters();
            if (parameters != null) {
                log.info("Restarting report job={}", dailyOrderSalesJob.getName());
                jobLauncher.run(dailyOrderSalesJob, parameters);
            }
        } catch (Exception ex) {
            log.error("Report job={} could not be restarted", dailyOrderSalesJob.getName(), ex);
        }
    }

    private JobParameters restartableParameters() {
        final JobInstance instance = jobExplorer.getLastJobInstance(dailyOrderSalesJob.getName());
        if (instance == null) {
            return null;
        }
        final JobExecution last = jobExplorer.getLastJobExecution(instance);
        if (last == null || (last.getStatus() != BatchStatus.FAILED && last.getStatus() != BatchStatus.STOPPED)) {
            return null;
        }
        return last.getJobParameters();
    }
}
