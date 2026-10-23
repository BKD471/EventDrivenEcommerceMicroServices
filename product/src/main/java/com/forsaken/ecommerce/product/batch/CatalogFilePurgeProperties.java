package com.forsaken.ecommerce.product.batch;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Retention for catalog import/export files kept in S3 ({@code product.catalog.purge.*}). */
@Validated
@ConfigurationProperties(prefix = "product.catalog.purge")
public record CatalogFilePurgeProperties(
        @DefaultValue("true") boolean enabled,
        @NotNull @DefaultValue("14d") Duration exportRetention,
        @NotNull @DefaultValue("30d") Duration processedRetention,
        @NotNull @DefaultValue("90d") Duration failedRetention
) {
}
