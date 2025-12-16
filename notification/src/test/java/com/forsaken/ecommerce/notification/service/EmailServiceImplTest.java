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
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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

    @BeforeEach
    void setUp() {
        mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
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
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        // Capture exact PDF datasource
        ArgumentCaptor<Map<PdfConstants, Object>> pdfCaptor =
                ArgumentCaptor.forClass(Map.class);
        when(pdfService.generateAndSendInvoice(pdfCaptor.capture()))
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
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
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
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(pdfService.generateAndSendInvoice(any()))
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
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
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
