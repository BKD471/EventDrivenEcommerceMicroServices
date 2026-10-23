package com.forsaken.ecommerce.common.batch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/**
 * Import from a service's application class to trim its Spring Batch metadata. Controlled by
 * {@code batch.metadata.purge.enabled} (default true), {@code .retention} (default 30d) and {@code .cron}.
 */
@Configuration
@ConditionalOnProperty(prefix = "batch.metadata.purge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BatchMetadataPurgeConfiguration {

    @Bean
    public BatchMetadataPurger batchMetadataPurger(
            final JdbcTemplate jdbcTemplate, final PlatformTransactionManager transactionManager) {
        return new BatchMetadataPurger(jdbcTemplate, new TransactionTemplate(transactionManager));
    }

    @Bean
    public BatchMetadataPurgeScheduler batchMetadataPurgeScheduler(
            final BatchMetadataPurger purger,
            @Value("${batch.metadata.purge.retention:30d}") final Duration retention) {
        return new BatchMetadataPurgeScheduler(purger, retention);
    }
}
