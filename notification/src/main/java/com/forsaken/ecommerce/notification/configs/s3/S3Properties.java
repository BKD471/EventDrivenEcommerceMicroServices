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

        /**
         * Expiration duration (in minutes) for S3 pre-signed URLs.
         *
         * <p>
         * This value controls how long a generated pre-signed URL remains valid
         * before expiring.
         * </p>
         *
         * <p>
         * <b>Constraints:</b>
         * </p>
         * <ul>
         *     <li>Minimum: 30 minutes</li>
         *     <li>Maximum: 1440 minutes (24 hours)</li>
         * </ul>
         *
         * <p>
         * This value is passed directly to AWS SDK when generating
         * {@code GetObjectPresignRequest}.
         * </p>
         */
        @Min(30)
        @Max(1440)
        @NotNull
        Long expiration
) {
}
