package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.configs.s3.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link S3ServiceImpl}.
 *
 * <p>
 * This test class verifies the behavior of the S3 service responsible for:
 * <ul>
 *     <li>Uploading invoice PDFs to Amazon S3</li>
 *     <li>Generating presigned download URLs for stored invoices</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Testing Strategy:</b>
 * <ul>
 *     <li>Uses Mockito with strict stubbing enabled.</li>
 *     <li>Positive (happy-path) tests avoid {@code any()} and rely on
 *         {@link org.mockito.ArgumentCaptor} for request verification.</li>
 *     <li>Negative and failure scenarios may use {@code any()} where
 *         argument precision is not relevant.</li>
 *     <li>Ensures correct S3 request construction, bucket usage,
 *         key naming conventions, and expiration configuration.</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class S3ServiceImplTest {

    private static final String bucketName = "invoice-bucket";

    @Mock
    private S3Properties s3Properties;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3ServiceImpl s3Service;

    /**
     * Initializes the {@link S3ServiceImpl} before each test.
     *
     * <p>
     * The S3 bucket name is stubbed once here since it is required
     * by all service methods.
     * </p>
     */
    @BeforeEach
    void setUp() {
        when(s3Properties.bucketName()).thenReturn(bucketName);
        s3Service = new S3ServiceImpl(
                s3Properties,
                s3Client,
                s3Presigner
        );
    }

    /**
     * Verifies that an invoice PDF is uploaded to S3 and the correct
     * object key is returned.
     *
     * <p>
     * This test ensures:
     * <ul>
     *     <li>The S3 key follows the expected naming convention:
     *         {@code invoices/{invoiceId}.pdf}</li>
     *     <li>The correct bucket name is used</li>
     *     <li>The uploaded content type is {@code application/pdf}</li>
     * </ul>
     * </p>
     */
    @Test
    void uploadInvoice_shouldUploadPdfAndReturnS3Key() {
        // Given
        final byte[] pdfBytes = "PDF_CONTENT".getBytes();
        final String invoiceId = "INV-123";

        final ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        final ArgumentCaptor<RequestBody> bodyCaptor =
                ArgumentCaptor.forClass(RequestBody.class);

        // When
        final String resultKey = s3Service.uploadInvoice(pdfBytes, invoiceId);

        // Then
        final PutObjectRequest request = requestCaptor.getValue();
        assertEquals("invoices/INV-123.pdf", resultKey);
        verify(s3Client).putObject(
                requestCaptor.capture(),
                bodyCaptor.capture()
        );
        assertEquals(bucketName, request.bucket());
        assertEquals("invoices/INV-123.pdf", request.key());
        assertEquals("application/pdf", request.contentType());
    }

    /**
     * Verifies that a valid presigned download URL is generated for
     * an existing S3 object.
     *
     * <p>
     * This test confirms:
     * <ul>
     *     <li>The presigned URL uses the configured expiration duration</li>
     *     <li>The correct S3 bucket and object key are used</li>
     *     <li>The generated URL is returned to the caller</li>
     * </ul>
     * </p>
     *
     * @throws Exception if URL construction fails unexpectedly
     */
    @Test
    void generatePresignedUrl_shouldReturnValidPresignedUrl() throws Exception {
        // Given
        final String key = "invoices/INV-123.pdf";
        final long expirationMinutes = 15;
        final URL expectedUrl = new URL("https://s3.aws.com/invoice");
        when(s3Properties.expiration()).thenReturn(expirationMinutes);
        final ArgumentCaptor<GetObjectPresignRequest> presignCaptor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        final PresignedGetObjectRequest presignedRequest =
                mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(expectedUrl);
        when(s3Presigner.presignGetObject(presignCaptor.capture()))
                .thenReturn(presignedRequest);

        // When
        final URL result = s3Service.generatePresignedUrl(key);

        // Then
        final GetObjectPresignRequest presignRequest =
                presignCaptor.getValue();
        final GetObjectRequest getRequest =
                presignRequest.getObjectRequest();
        assertEquals(
                Duration.ofMinutes(expirationMinutes),
                presignRequest.signatureDuration()
        );
        assertEquals(expectedUrl, result);
        assertEquals(bucketName, getRequest.bucket());
        assertEquals(key, getRequest.key());
    }

    /**
     * Verifies that exceptions thrown by the S3 client during upload
     * are propagated to the caller.
     *
     * <p>
     * This ensures the service does not silently swallow infrastructure
     * failures.
     * </p>
     */
    @Test
    void uploadInvoice_shouldPropagateException_whenS3Fails() {
        // Given
        doThrow(new RuntimeException("S3 down"))
                .when(s3Client)
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));

        // When / Then
        assertThrows(RuntimeException.class,
                () -> s3Service.uploadInvoice(
                        "PDF".getBytes(),
                        "INV-FAIL"
                )
        );
    }

    /**
     * Verifies that exceptions thrown during presigned URL generation
     * are propagated to the caller.
     *
     * <p>
     * Covers scenarios such as invalid credentials, AWS service
     * unavailability, or misconfiguration.
     * </p>
     */
    @Test
    void generatePresignedUrl_shouldPropagateException_whenPresignerFails() {
        // Given
        when(s3Properties.expiration()).thenReturn(10L);
        doThrow(new RuntimeException("Presign failure"))
                .when(s3Presigner)
                .presignGetObject(any(GetObjectPresignRequest.class));

        // When / Then
        assertThrows(RuntimeException.class,
                () -> s3Service.generatePresignedUrl("invoices/INV-1.pdf")
        );
    }
}
