package com.forsaken.ecommerce.product.batch;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Tuning for the catalog import, export and low-stock batch jobs ({@code product.catalog.batch.*}). */
@Validated
@ConfigurationProperties(prefix = "product.catalog.batch")
public record CatalogBatchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") @Min(1) int chunkSize,
        @DefaultValue("3") @Min(1) int retryLimit,
        @DefaultValue("100") @Min(0) int skipLimit,
        /** Stock strictly below this raises a low-stock alert. */
        @DefaultValue("10") @Min(0) double lowStockThreshold,
        /** An import file whose job failed this many times is moved to the failed prefix. */
        @DefaultValue("3") @Min(1) int maxImportAttempts,
        @NotBlank @DefaultValue("imports/incoming/") String importPrefix,
        @NotBlank @DefaultValue("imports/processed/") String processedPrefix,
        @NotBlank @DefaultValue("imports/failed/") String failedPrefix,
        @NotBlank @DefaultValue("exports/") String exportPrefix
) {
}
