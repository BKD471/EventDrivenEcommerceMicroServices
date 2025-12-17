package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.configs.invoice.InvoiceProperties;
import com.forsaken.ecommerce.notification.models.PaymentMethod;
import com.forsaken.ecommerce.notification.models.PdfConstants;
import com.forsaken.ecommerce.notification.models.Product;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import net.sf.jasperreports.engine.JRException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.forsaken.ecommerce.notification.models.EmailTemplates.ORDER_CONFIRMATION;
import static com.forsaken.ecommerce.notification.models.EmailTemplates.PAYMENT_CONFIRMATION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailServiceImpl}.
 *
 * <p>
 * This test suite verifies the behavior of the email notification service
 * responsible for sending:
 * <ul>
 *     <li>Payment success emails with invoice PDF and presigned S3 URL</li>
 *     <li>Order confirmation emails with product details</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Testing approach:</b>
 * <ul>
 *     <li>Uses Mockito to mock all external dependencies
 *         (mail sender, template engine, PDF service, S3 service).</li>
 *     <li>Focuses on verifying business behavior rather than implementation details.</li>
 *     <li>Positive test cases avoid broad argument matchers and validate actual data.</li>
 *     <li>Negative test cases validate graceful error handling without propagating exceptions.</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Key guarantees validated:</b>
 * <ul>
 *     <li>Emails are sent with correct template variables.</li>
 *     <li>Invoices are generated, uploaded, and linked correctly.</li>
 *     <li>Email failures (PDF generation or SMTP issues) do not break application flow.</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private SpringTemplateEngine templateEngine;

    @Mock
    private IPdfService pdfService;

    @Mock
    private IS3Service s3Service;

    @Mock
    private InvoiceProperties invoiceProperties;

    private EmailServiceImpl emailService;

    private MimeMessage mimeMessage;

    /**
     * Initializes common test fixtures before each test execution.
     *
     * <p>
     * A real {@link MimeMessage} instance is created and returned from the mocked
     * {@link JavaMailSender} to allow {@link MimeMessageHelper} to function
     * correctly without triggering {@link NullPointerException}s.
     * </p>
     *
     * <p>
     * This setup mirrors production behavior closely while still keeping
     * all external interactions fully mocked.
     * </p>
     */
    @BeforeEach
    void setUp() {
        mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(invoiceProperties.senderEmailAddress())
                .thenReturn("noreply@test.com");
        emailService = new EmailServiceImpl(
                mailSender,
                templateEngine,
                pdfService,
                s3Service,
                invoiceProperties
        );
    }

    /**
     * Verifies that a payment success email is sent successfully when all
     * dependent services behave as expected.
     *
     * <p>
     * This test ensures that:
     * <ul>
     *     <li>An invoice PDF is generated with correct customer and payment data.</li>
     *     <li>The invoice is uploaded to S3 and a presigned download URL is created.</li>
     *     <li>The email template receives the correct variables.</li>
     *     <li>The email is ultimately sent using {@link JavaMailSender}.</li>
     * </ul>
     * </p>
     *
     * @throws Exception if any unexpected error occurs during test execution
     */
    @Test
    void sendPaymentSuccessEmail_shouldSendEmailWithPdfAndPresignedUrl() throws Exception {
        // Given
        final String email = "test@example.com";
        final String customerName = "Bhaskar";
        final BigDecimal amount = new BigDecimal("150.50");
        final String orderRef = "ORD-1";
        final PaymentMethod paymentMethod = PaymentMethod.PAYPAL;
        final LocalDateTime paymentDate = LocalDateTime.of(2024, 1, 1, 10, 0);

        final byte[] pdfBytes = "PDF_BYTES".getBytes();
        final String invoiceKey = "invoices/INV-1.pdf";
        final URL presignedUrl = new URL("https://s3.aws.com/inv");
        ArgumentCaptor<Map<PdfConstants, Object>> pdfCaptor =
                ArgumentCaptor.forClass(Map.class);
        when(pdfService.generateInvoicePdf(pdfCaptor.capture()))
                .thenReturn(pdfBytes);
        when(s3Service.uploadInvoice(eq(pdfBytes), anyString()))
                .thenReturn(invoiceKey);
        when(s3Service.generatePresignedUrl(invoiceKey))
                .thenReturn(presignedUrl);
        final ArgumentCaptor<Context> contextCaptor =
                ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(
                eq(PAYMENT_CONFIRMATION.getTemplate()),
                contextCaptor.capture()
        )).thenReturn("<html>payment</html>");

        // When
        emailService.sendPaymentSuccessEmail(
                email,
                customerName,
                amount,
                orderRef,
                paymentMethod,
                paymentDate
        );

        // Then
        final Map<PdfConstants, Object> pdfData = pdfCaptor.getValue();
        assertEquals(customerName, pdfData.get(PdfConstants.CUSTOMER_NAME));
        assertEquals(email, pdfData.get(PdfConstants.EMAIL));
        assertEquals(amount, pdfData.get(PdfConstants.AMOUNT));

        final Context ctx = contextCaptor.getValue();
        assertEquals(customerName, ctx.getVariable("customerName"));
        assertEquals(amount, ctx.getVariable("amount"));
        assertEquals(orderRef, ctx.getVariable("orderReference"));
        assertEquals(presignedUrl.toString(), ctx.getVariable("downloadUrl"));
        verify(mailSender).send(mimeMessage);
    }

    /**
     * Verifies that an order confirmation email is sent successfully with
     * correct order and product details.
     *
     * <p>
     * This test validates:
     * <ul>
     *     <li>Correct population of template variables such as customer name,
     *         order reference, total amount, and product list.</li>
     *     <li>Successful dispatch of the email using the mail sender.</li>
     * </ul>
     * </p>
     *
     * @throws Exception if any unexpected error occurs during test execution
     */
    @Test
    void sendOrderConfirmationEmail_shouldSendOrderEmail() throws Exception {
        // Given
        final String email = "order@example.com";
        final String customerName = "Bhaskar";
        final BigDecimal amount = new BigDecimal("300");
        final String orderRef = "ORD-99";
        final List<Product> products = List.of(
                constructProduct(1, BigDecimal.valueOf(200)),
                constructProduct(2, BigDecimal.valueOf(100))
        );
        final ArgumentCaptor<Context> contextCaptor =
                ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(
                eq(ORDER_CONFIRMATION.getTemplate()),
                contextCaptor.capture()
        )).thenReturn("<html>order</html>");

        // When
        emailService.sendOrderConfirmationEmail(
                email,
                customerName,
                amount,
                orderRef,
                products
        );

        // Then
        final Context ctx = contextCaptor.getValue();
        assertEquals(customerName, ctx.getVariable("customerName"));
        assertEquals(amount, ctx.getVariable("totalAmount"));
        assertEquals(orderRef, ctx.getVariable("orderReference"));
        assertEquals(products, ctx.getVariable("products"));
        verify(mailSender).send(mimeMessage);
    }

    /**
     * Verifies that a failure during invoice PDF generation does not
     * cause the payment success email flow to throw an exception.
     *
     * <p>
     * This test ensures that:
     * <ul>
     *     <li>PDF generation errors are handled gracefully.</li>
     *     <li>No attempt is made to send an email when invoice generation fails.</li>
     *     <li>The method does not propagate the exception to the caller.</li>
     * </ul>
     * </p>
     *
     * @throws Exception if test setup fails
     */
    @Test
    void sendPaymentSuccessEmail_shouldNotThrowWhenPdfFails() throws Exception {
        when(pdfService.generateInvoicePdf(any()))
                .thenThrow(new JRException("PDF error"));

        assertDoesNotThrow(() ->
                emailService.sendPaymentSuccessEmail(
                        "fail@example.com",
                        "Fail",
                        BigDecimal.TEN,
                        "ORD-F",
                        PaymentMethod.CREDIT_CARD,
                        LocalDateTime.now()
                )
        );

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    /**
     * Verifies that SMTP or mail-sending failures do not propagate exceptions
     * to the caller when sending an order confirmation email.
     *
     * <p>
     * This test simulates a mail transport failure and ensures:
     * <ul>
     *     <li>The email template is still processed correctly.</li>
     *     <li>The {@link EmailServiceImpl} handles {@link org.springframework.mail.MailException}
     *         gracefully.</li>
     *     <li>No exception is thrown back to the caller.</li>
     * </ul>
     * </p>
     */
    @Test
    void sendOrderConfirmationEmail_shouldHandleMailException() {
        // Given
        when(templateEngine.process(
                eq(ORDER_CONFIRMATION.getTemplate()),
                any(Context.class)
        )).thenReturn("<html>order</html>");

        doThrow(new MailSendException("SMTP down"))
                .when(mailSender)
                .send(any(MimeMessage.class));

        // When -> Then
        assertDoesNotThrow(() ->
                emailService.sendOrderConfirmationEmail(
                        "error@example.com",
                        "Err",
                        BigDecimal.ONE,
                        "ORD-E",
                        List.of()
                )
        );
    }

    /**
     * Verifies that invalid input errors thrown by the S3 service
     * (for example, invalid invoice identifiers) are handled gracefully.
     *
     * <p>
     * This test ensures that:
     * <ul>
     *     <li>{@link IllegalArgumentException}s from {@link IS3Service}
     *         do not propagate beyond the email service.</li>
     *     <li>No email is sent when invoice upload fails.</li>
     *     <li>The failure does not break asynchronous execution.</li>
     * </ul>
     * </p>
     */
    @Test
    void shouldHandleIllegalArgumentExceptionFromS3Gracefully() throws Exception {
        // given
        when(pdfService.generateInvoicePdf(any()))
                .thenReturn("PDF".getBytes());

        doThrow(new IllegalArgumentException("Invalid invoiceId"))
                .when(s3Service)
                .uploadInvoice(any(), any());

        // when + then
        assertDoesNotThrow(() ->
                emailService.sendPaymentSuccessEmail(
                        "user@test.com",
                        "John Doe",
                        BigDecimal.TEN,
                        "ORD-FAIL",
                        PaymentMethod.PAYPAL,
                        LocalDateTime.now()
                )
        );
        verify(s3Service).uploadInvoice(any(), any());
        verify(s3Service, never()).generatePresignedUrl(any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    /**
     * Verifies that AWS SDK runtime failures occurring during invoice upload
     * are handled gracefully by the email service.
     *
     * <p>
     * This test simulates an {@link software.amazon.awssdk.core.exception.SdkClientException}
     * thrown by {@link IS3Service#uploadInvoice(byte[], String)}, which may occur due to:
     * </p>
     * <ul>
     *     <li>Temporary network outages</li>
     *     <li>AWS authentication or credential issues</li>
     *     <li>S3 service unavailability</li>
     * </ul>
     *
     * <p>
     * <b>Expected behavior:</b>
     * </p>
     * <ul>
     *     <li>The exception is caught internally and does not propagate to the caller</li>
     *     <li>The email sending flow is aborted immediately</li>
     *     <li>No attempt is made to generate a presigned URL</li>
     *     <li>No email is sent via {@link JavaMailSender}</li>
     * </ul>
     *
     * <p>
     * <b>Why this matters:</b>
     * </p>
     * <ul>
     *     <li>AWS SDK exceptions are unchecked and can otherwise crash async execution</li>
     *     <li>Email delivery must not occur when invoice storage fails</li>
     *     <li>The system must remain resilient to transient infrastructure failures</li>
     * </ul>
     *
     * <p>
     * This test guarantees that AWS infrastructure errors do not break the
     * notification workflow or leak partially constructed emails.
     * </p>
     */
    @Test
    void sendPaymentSuccessEmail_shouldHandleAwsSdkExceptionGracefully() throws Exception {
        // Given
        final byte[] pdfBytes = "PDF".getBytes();
        when(pdfService.generateInvoicePdf(any()))
                .thenReturn(pdfBytes);
        // Simulate AWS SDK runtime failure (network / auth / service error)
        doThrow(SdkClientException.builder()
                .message("AWS S3 unavailable")
                .build())
                .when(s3Service)
                .uploadInvoice(any(), any());

        // When -> Then
        assertDoesNotThrow(() ->
                emailService.sendPaymentSuccessEmail(
                        "user@test.com",
                        "John Doe",
                        BigDecimal.TEN,
                        "ORD-AWS",
                        PaymentMethod.PAYPAL,
                        LocalDateTime.now()
                )
        );

        // Email must NOT be sent
        verify(mailSender, never()).send(any(MimeMessage.class));
        // Presigned URL must NOT be generated
        verify(s3Service, never()).generatePresignedUrl(any());
    }

    /**
     * Constructs a sample {@link Product} instance for testing purposes.
     *
     * @param productId unique identifier of the product
     * @param price     price of the product
     * @return populated {@link Product} instance
     */
    private Product constructProduct(final Integer productId, final BigDecimal price) {
        return Product.builder()
                .productId(productId)
                .name("Product-" + productId)
                .price(price)
                .description("Description-1")
                .quantity(1)
                .build();
    }
}
