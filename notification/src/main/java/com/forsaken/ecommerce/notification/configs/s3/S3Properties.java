package com.forsaken.ecommerce.notification.configs.s3;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(

        @NotBlank
        String bucketName,


        @Min(30)
        @Max(1440)
        @NotNull
        Long expiration
) {
}
