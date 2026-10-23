package com.forsaken.ecommerce.common.batch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Deletes finished Spring Batch metadata. Every scheduled job run adds a job instance, an execution and its step
 * executions, so the {@code BATCH_*} tables grow forever unless they are trimmed.
 *
 * <p>Only {@code COMPLETED} and {@code ABANDONED} executions that ended before the cutoff are removed. {@code FAILED}
 * and {@code STOPPED} executions are kept because the schedulers restart them, and running ones are never touched.
 */
public class BatchMetadataPurger {

    private static final String OLD_EXECUTIONS = """
            SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION
            WHERE STATUS IN ('COMPLETED', 'ABANDONED') AND END_TIME < ?""";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public BatchMetadataPurger(final JdbcTemplate jdbc, final TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    /** @return the number of job executions deleted */
    public int purgeOlderThan(final Duration retention) {
        final Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minus(retention));
        final Integer deleted = transaction.execute(status -> {
            jdbc.update("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID IN ("
                    + "SELECT STEP_EXECUTION_ID FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID IN ("
                    + OLD_EXECUTIONS + "))", cutoff);
            jdbc.update("DELETE FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID IN (" + OLD_EXECUTIONS + ")", cutoff);
            jdbc.update("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT WHERE JOB_EXECUTION_ID IN ("
                    + OLD_EXECUTIONS + ")", cutoff);
            jdbc.update("DELETE FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID IN ("
                    + OLD_EXECUTIONS + ")", cutoff);
            final int executions = jdbc.update(
                    "DELETE FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID IN (" + OLD_EXECUTIONS + ")", cutoff);
            jdbc.update("DELETE FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID NOT IN ("
                    + "SELECT JOB_INSTANCE_ID FROM BATCH_JOB_EXECUTION)");
            return executions;
        });
        return deleted == null ? 0 : deleted;
    }
}
