package com.forsaken.ecommerce.common.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchMetadataPurgerTest {

    private JdbcTemplate jdbc;
    private BatchMetadataPurger purger;

    @BeforeEach
    void setUp() {
        final DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE BATCH_JOB_INSTANCE (JOB_INSTANCE_ID BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE BATCH_JOB_EXECUTION (JOB_EXECUTION_ID BIGINT PRIMARY KEY, "
                + "JOB_INSTANCE_ID BIGINT, STATUS VARCHAR(10), END_TIME TIMESTAMP)");
        jdbc.execute("CREATE TABLE BATCH_JOB_EXECUTION_PARAMS (JOB_EXECUTION_ID BIGINT)");
        jdbc.execute("CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT (JOB_EXECUTION_ID BIGINT)");
        jdbc.execute("CREATE TABLE BATCH_STEP_EXECUTION (STEP_EXECUTION_ID BIGINT PRIMARY KEY, JOB_EXECUTION_ID BIGINT)");
        jdbc.execute("CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT (STEP_EXECUTION_ID BIGINT)");
        purger = new BatchMetadataPurger(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    private void run(final long id, final String status, final LocalDateTime endTime) {
        jdbc.update("INSERT INTO BATCH_JOB_INSTANCE VALUES (?)", id);
        jdbc.update("INSERT INTO BATCH_JOB_EXECUTION VALUES (?, ?, ?, ?)", id, id, status,
                endTime == null ? null : Timestamp.valueOf(endTime));
        jdbc.update("INSERT INTO BATCH_JOB_EXECUTION_PARAMS VALUES (?)", id);
        jdbc.update("INSERT INTO BATCH_JOB_EXECUTION_CONTEXT VALUES (?)", id);
        jdbc.update("INSERT INTO BATCH_STEP_EXECUTION VALUES (?, ?)", id * 10, id);
        jdbc.update("INSERT INTO BATCH_STEP_EXECUTION_CONTEXT VALUES (?)", id * 10);
    }

    private int count(final String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Test
    void removesOldFinishedRunsWithAllTheirRows() {
        final LocalDateTime old = LocalDateTime.now().minusDays(60);
        run(1, "COMPLETED", old);
        run(2, "ABANDONED", old);

        assertEquals(2, purger.purgeOlderThan(Duration.ofDays(30)));

        for (final String table : new String[]{"BATCH_JOB_INSTANCE", "BATCH_JOB_EXECUTION",
                "BATCH_JOB_EXECUTION_PARAMS", "BATCH_JOB_EXECUTION_CONTEXT",
                "BATCH_STEP_EXECUTION", "BATCH_STEP_EXECUTION_CONTEXT"}) {
            assertEquals(0, count(table), table);
        }
    }

    @Test
    void keepsRecentFailedStoppedAndRunningRuns() {
        final LocalDateTime old = LocalDateTime.now().minusDays(60);
        run(1, "COMPLETED", LocalDateTime.now().minusDays(1));
        run(2, "FAILED", old);
        run(3, "STOPPED", old);
        run(4, "STARTED", null);

        assertEquals(0, purger.purgeOlderThan(Duration.ofDays(30)));

        assertEquals(4, count("BATCH_JOB_EXECUTION"));
        assertEquals(4, count("BATCH_JOB_INSTANCE"));
        assertEquals(4, count("BATCH_STEP_EXECUTION_CONTEXT"));
    }
}
