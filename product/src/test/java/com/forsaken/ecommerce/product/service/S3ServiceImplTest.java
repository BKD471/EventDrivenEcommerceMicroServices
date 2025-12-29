package com.forsaken.ecommerce.product.service;


import com.forsaken.ecommerce.product.configs.s3.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link S3ServiceImpl}, validating correct generation of
 * AWS S3 presigned upload and download URLs using {@link S3Presigner}.
 *
 * <p>This test class ensures:
 * <ul>
 *     <li>The presigned upload and download URL generation flows are executed correctly</li>
 *     <li>Correct S3 bucket and expiration properties are used</li>
 *     <li>Appropriate exceptions are thrown when AWS SDK returns invalid or null responses</li>
 *     <li>Keys for uploaded files are generated with proper prefixes and formatting</li>
 * </ul>
 *
 * <p>All AWS interactions are fully mocked using Mockito to isolate service logic.
 *
 * <p>{@code Strictness.LENIENT} is intentionally used to allow relaxed stubbing for
 * AWS-related behaviors that are internal to builder consumers.
 */
@ExtendWith(MockitoExtension.class)
class S3ServiceImplTest {

    @Mock
    private S3Properties s3Properties;

    @Mock
    private S3Presigner presigner;

    @Mock
    private PresignedPutObjectRequest presignedPutObjectRequest;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    private S3ServiceImpl s3Service;

    @BeforeEach
    void setup() {
        s3Service = new S3ServiceImpl(s3Properties, presigner);
    }

    /**
     * Verifies that a valid presigned upload URL is generated when AWS correctly
     * returns a non-null {@link PresignedPutObjectRequest} containing a URL.
     *
     * <p>Ensures that:
     * <ul>
     *     <li>The generated upload URL matches the mocked AWS value</li>
     *     <li>A properly formatted S3 key is created (prefix + UUID + filename suffix)</li>
     *     <li>The returned map contains both the upload URL and the generated key</li>
     * </ul>
     *
     * @throws Exception if URL creation fails (only for test setup)
     */
    @Test
    void generatePresignedUploadUrl_ShouldReturnUrlAndKey() throws Exception {
        // Given
        when(s3Properties.bucketName()).thenReturn("test-bucket");
        when(s3Properties.expiration()).thenReturn(30L);
        final URL fakeUrl = new URL("https://s3.com/upload-url");
        // Mock the URL returned by AWS Presigned request
        when(presignedPutObjectRequest.url())
                .thenReturn(fakeUrl);
        // Mock Consumer< GetObjectPresignRequest.Builder >
        when(presigner.presignPutObject(any(Consumer.class)))
                .thenAnswer(inv -> {
                    PutObjectPresignRequest.Builder builder =
                            PutObjectPresignRequest.builder();
                    Consumer<PutObjectPresignRequest.Builder> consumer =
                            inv.getArgument(0);
                    consumer.accept(builder);
                    return presignedPutObjectRequest;
                });

        // When
        final Map<String, String> result =
                s3Service.generatePresignedUploadUrl("photo.png", "image/png");

        // Then
        assertEquals("https://s3.com/upload-url", result.get("uploadUrl"));
        assertTrue(result.get("key").contains("uploads/"));
        assertTrue(result.get("key").endsWith("_photo.png"));
    }

    /**
     * Ensures that the service throws {@link IllegalStateException} when AWS SDK
     * returns {@code null} instead of a valid {@link PresignedPutObjectRequest}.
     *
     * <p>This validates defensive error handling against unexpected AWS behavior.
     */
    @Test
    void generatePresignedUploadUrl_ShouldThrowIllegalState_WhenPresignerReturnsNull() {
        // Given
        // Simulate AWS returning null instead of a PresignedPutObjectRequest
        when(presigner.presignPutObject(any(Consumer.class)))
                .thenReturn(null);

        // When
        final IllegalStateException exception =
                assertThrows(IllegalStateException.class, () ->
                        s3Service.generatePresignedUploadUrl("photo.png", "image/png")
                );

        // Then
        assertEquals("Unable to generate presigned upload URL", exception.getMessage());
    }

    /**
     * Ensures the service throws {@link IllegalStateException} when AWS returns a
     * {@link PresignedPutObjectRequest} whose internal URL is {@code null}.
     *
     * <p>This prevents the application from returning invalid or unusable upload URLs.
     */
    @Test
    void generatePresignedUploadUrl_ShouldThrowIllegalState_WhenPresignerReturnsUrlNull() {
        // Given
        when(s3Properties.bucketName()).thenReturn("test-bucket");
        when(s3Properties.expiration()).thenReturn(30L);
        final String imageUrl = "https://cdn.com/uploads/img.png";
        // Simulate AWS returning presigned url null
        when(presigner.presignPutObject(any(Consumer.class)))
                .thenAnswer(inv -> {
                    PutObjectPresignRequest.Builder builder =
                            PutObjectPresignRequest.builder();
                    Consumer<PutObjectPresignRequest.Builder> consumer =
                            inv.getArgument(0);
                    consumer.accept(builder);
                    return presignedPutObjectRequest;
                });
        when(presignedPutObjectRequest.url())
                .thenReturn(null);

        // When
        final IllegalStateException exception =
                assertThrows(IllegalStateException.class, () ->
                        s3Service.generatePresignedUploadUrl(imageUrl, "image/png")
                );

        // Then
        assertEquals("Unable to generate presigned upload URL", exception.getMessage());
    }

    /**
     * Validates that the download presigned URL generation succeeds when AWS
     * returns a valid {@link PresignedGetObjectRequest} with a non-null URL.
     *
     * <p>Ensures that:
     * <ul>
     *     <li>The returned value matches the mocked signed URL</li>
     *     <li>The AWS builder consumer simulation is executed correctly</li>
     * </ul>
     *
     * @throws Exception for URL initialization inside the test
     */
    @Test
    void generatePresignedDownloadUrl_ShouldReturnPresignedUrl() throws Exception {
        // Given
        when(s3Properties.bucketName()).thenReturn("test-bucket");
        when(s3Properties.expiration()).thenReturn(30L);
        final String imageUrl = "https://cdn.com/uploads/img_123.png";
        final URL fakeDownloadUrl = new URL("https://s3.com/download-url");
        // Mock the URL returned by AWS Presigned request
        when(presignedGetObjectRequest.url())
                .thenReturn(fakeDownloadUrl);
        when(presigner.presignGetObject(any(Consumer.class)))
                .thenAnswer(inv -> {
                    GetObjectPresignRequest.Builder builder =
                            GetObjectPresignRequest.builder();
                    Consumer<GetObjectPresignRequest.Builder> consumer =
                            inv.getArgument(0);
                    consumer.accept(builder); // simulate AWS builder population
                    return presignedGetObjectRequest;
                });

        // When
        final String resultUrl = s3Service.generatePresignedDownloadUrl(imageUrl);
        // Then
        assertEquals("https://s3.com/download-url", resultUrl);
    }

    /**
     * Ensures that an {@link IllegalStateException} is thrown when AWS returns
     * {@code null} instead of a valid {@link PresignedGetObjectRequest}.
     *
     * <p>This validates error handling for download URL generation failures.
     */
    @Test
    void generatePresignedDownloadUrl_ShouldThrowIllegalState_WhenPresignerReturnsNull() {
        // Given
        final String imageUrl = "https://cdn.com/uploads/img.png";
        // Simulate AWS returning null
        when(presigner.presignGetObject(any(Consumer.class)))
                .thenReturn(null);

        // When
        final IllegalStateException exception =
                assertThrows(IllegalStateException.class, () ->
                        s3Service.generatePresignedDownloadUrl(imageUrl)
                );

        // Then
        assertEquals("Unable to generate presigned download URL", exception.getMessage());
    }

    /**
     * Ensures that the service throws {@link IllegalStateException} when AWS SDK
     * returns a {@link PresignedGetObjectRequest} whose URL is {@code null}.
     *
     * <p>Prevents the API from returning unusable presigned URLs to clients.
     */
    @Test
    void generatePresignedDownloadUrl_ShouldThrowIllegalState_WhenPresignerReturnsUrlNull() {
        // Given
        when(s3Properties.bucketName()).thenReturn("test-bucket");
        when(s3Properties.expiration()).thenReturn(30L);
        final String imageUrl = "https://cdn.com/uploads/img.png";
        // Simulate AWS returning presigned url null
        when(presigner.presignGetObject(any(Consumer.class)))
                .thenAnswer(inv -> {
                    GetObjectPresignRequest.Builder builder =
                            GetObjectPresignRequest.builder();
                    Consumer<GetObjectPresignRequest.Builder> consumer =
                            inv.getArgument(0);
                    consumer.accept(builder); // simulate AWS builder population
                    return presignedGetObjectRequest;
                });
        when(presignedGetObjectRequest.url())
                .thenReturn(null);

        // When
        final IllegalStateException exception =
                assertThrows(IllegalStateException.class, () ->
                        s3Service.generatePresignedDownloadUrl(imageUrl)
                );

        // Then
        assertEquals("Unable to generate presigned download URL", exception.getMessage());
    }
}
