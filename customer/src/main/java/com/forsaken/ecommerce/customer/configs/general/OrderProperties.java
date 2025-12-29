package com.forsaken.ecommerce.customer.configs.general;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "general.configurations")
public record OrderProperties(

        @Min(1)
        @Max(100)
        int maxPageSize,

        @Min(1)
        @Max(100)
        int defaultPageSize,

        @Min(0)
        @Max(500)
        int defaultPageNumber
) {
}