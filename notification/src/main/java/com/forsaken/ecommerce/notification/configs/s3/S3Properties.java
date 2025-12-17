package com.forsaken.ecommerce.notification.configs.s3;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Amazon S3 integration.
 *
 * <p>
 * This record binds configuration values defined under the
 * {@code aws.s3} prefix in application configuration files
 * (e.g. {@code application.yml} or {@code application.properties}).
 * </p>
 *
 * <p>
 * These properties are used by S3-related services to:
 * </p>
 * <ul>
 *     <li>Determine the target S3 bucket for invoice storage</li>
 *     <li>Configure expiration duration for generated pre-signed URLs</li>
 * </ul>
 *
 * <p>
 * The class is annotated with {@link Validated}, ensuring that all
 * constraints are checked at application startup. If any constraint
 * is violated, the application will fail fast with a clear
 * configuration error.
 * </p>
 */
@Validated
@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(

        /**
         * Name of the Amazon S3 bucket used to store invoice PDFs.
         *
         * <p>
         * This bucket must already exist in the configured AWS account
         * and region. The application does not attempt to create it.
         * </p>
         *
         * <p>
         * <b>Constraints:</b>
         * </p>
         * <ul>
         *     <li>Must not be {@code null}, empty, or blank</li>
         * </ul>
         */
        @NotBlank
        String bucketName,

        /**
         * Expiration duration (in minutes) for S3 pre-signed URLs.
         *
         * <p>
         * This value controls how long a generated pre-signed URL remains valid
         * before expiring. Once expired, the URL can no longer be used to
         * download the associated object.
         * </p>
         *
         * <p>
         * The value is passed directly to the AWS SDK when constructing
         * {@code GetObjectPresignRequest}.
         * </p>
         *
         * <p>
         * <b>Constraints:</b>
         * </p>
         * <ul>
         *     <li>Minimum: 30 minutes</li>
         *     <li>Maximum: 1440 minutes (24 hours)</li>
         *     <li>Must not be {@code null}</li>
         * </ul>
         *
         * <p>
         * These limits help prevent:
         * </p>
         * <ul>
         *     <li>Excessively short-lived URLs that break user experience</li>
         *     <li>Overly long-lived URLs that may pose security risks</li>
         * </ul>
         */
        @NotNull
        @Min(30)
        @Max(1440)
        Long expiration
) {
}
