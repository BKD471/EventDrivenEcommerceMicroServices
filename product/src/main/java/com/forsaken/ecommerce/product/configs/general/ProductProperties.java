package com.forsaken.ecommerce.product.configs.general;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


@Validated
@ConfigurationProperties(prefix = "general.configurations")
public record ProductProperties(

        @Min(1)
        @Max(100)
        int maxPageSize,

        @Min(1)
        @Max(100)
        int defaultPageSize,

        @Min(0)
        @Max(1_000)
        int defaultPageNumber
) {
}
