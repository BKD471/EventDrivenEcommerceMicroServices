package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.configs.invoice.InvoiceProperties;
import com.forsaken.ecommerce.notification.models.PdfConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PdfServiceImpl}.
 *
 * <p>
 * This test suite verifies the behavior of the invoice PDF generation service
 * backed by JasperReports.
 * </p>
 *
 * <p>
 * <b>Testing strategy:</b>
 * <ul>
 *     <li>Positive test executes the real JasperReports pipeline using
 *         classpath-based templates and resources.</li>
 *     <li>Configuration paths are provided via mocked {@link InvoiceProperties}
 *         to keep the tests deterministic.</li>
 *     <li>Negative tests validate fail-fast behavior for misconfiguration and
 *         missing mandatory invoice data.</li>
 *     <li>Parameterized tests are used to ensure all required invoice fields
 *         are validated consistently.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Mockito is used in strict mode to prevent unnecessary stubbing and to ensure
 * tests accurately reflect real execution paths.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PdfServiceImplTest {

    @Mock
    private InvoiceProperties invoiceProperties;
    private PdfServiceImpl pdfService;

    /**
     * Creates a fresh {@link PdfServiceImpl} instance before each test.
     *
     * <p>
     * No configuration values are stubbed here to avoid unnecessary stubbing.
     * Individual tests provide only the configuration required for their
     * execution paths.
     * </p>
     */
    @BeforeEach
    void setUp() {
        pdfService = new PdfServiceImpl(invoiceProperties);
    }

    /**
     * Verifies that a valid invoice PDF is generated when all required
     * invoice data and configuration paths are provided.
     *
     * <p>
     * This test ensures that:
     * <ul>
     *     <li>The JasperReports template is successfully compiled.</li>
     *     <li>All required invoice fields are mapped correctly.</li>
     *     <li>A non-empty PDF byte array is produced.</li>
     *     <li>The generated output is a valid PDF document, verified using
     *         the standard "%PDF" file signature.</li>
     * </ul>
     * </p>
     *
     * @throws Exception if PDF generation fails unexpectedly
     */
    @Test
    void generateInvoicePdf_shouldGenerateValidPdfBytes() throws Exception {
        // Given
        stubAllInvoicePaths();
        final Map<PdfConstants, Object> datasource = new EnumMap<>(PdfConstants.class);
        datasource.put(PdfConstants.INVOICE_NUM, "INV-1001");
        datasource.put(PdfConstants.CUSTOMER_NAME, "Bhaskar Das");
        datasource.put(PdfConstants.EMAIL, "bhaskar@test.com");
        datasource.put(PdfConstants.AMOUNT, new BigDecimal("150.50"));
        datasource.put(PdfConstants.PAYMENT_METHOD, "PAYPAL");
        datasource.put(PdfConstants.PAYMENT_DATE, LocalDateTime.now().toString());

        // When
        final byte[] pdfBytes = pdfService.generateInvoicePdf(datasource);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        // PDF magic number check (%PDF)
        assertEquals('%', pdfBytes[0]);
        assertEquals('P', pdfBytes[1]);
        assertEquals('D', pdfBytes[2]);
        assertEquals('F', pdfBytes[3]);
    }

    /**
     * Verifies that an {@link IOException} is thrown when the JasperReports
     * template path is invalid or the template cannot be loaded.
     *
     * <p>
     * This test simulates a misconfiguration scenario where the JRXML template
     * is missing from the classpath.
     * </p>
     */
    @Test
    void generateInvoicePdf_shouldThrowIOException_whenTemplatePathInvalid() {
        // given
        when(invoiceProperties.jasperTemplatePath())
                .thenReturn("reports/does-not-exist.jrxml");

        final Map<PdfConstants, Object> datasource = new EnumMap<>(PdfConstants.class);
        datasource.put(PdfConstants.INVOICE_NUM, "INV-2");
        datasource.put(PdfConstants.CUSTOMER_NAME, "Bhaskar");
        datasource.put(PdfConstants.EMAIL, "bhaskar@test.com");
        datasource.put(PdfConstants.AMOUNT, BigDecimal.TEN);
        datasource.put(PdfConstants.PAYMENT_METHOD, "UPI");
        datasource.put(PdfConstants.PAYMENT_DATE, LocalDateTime.now().toString());

        // when -> then
        assertThrows(IOException.class,
                () -> pdfService.generateInvoicePdf(datasource));
    }

    /**
     * Verifies that PDF generation fails fast when any mandatory invoice
     * field is missing.
     *
     * <p>
     * This parameterized test iterates over all {@link PdfConstants} values
     * and removes one field at a time from the input datasource. The service
     * is expected to throw an {@link IllegalArgumentException} identifying
     * the missing field explicitly.
     * </p>
     *
     * <p>
     * This approach ensures comprehensive validation coverage and automatically
     * includes newly added invoice fields without requiring additional tests.
     * </p>
     *
     * @param missingKey the invoice field intentionally omitted for this test case
     */
    @ParameterizedTest(name = "Missing field should fail: {0}")
    @EnumSource(PdfConstants.class)
    void generateInvoicePdf_shouldThrowException_whenAnyMandatoryFieldMissing(
            PdfConstants missingKey
    ) {
        // Given
        stubAllInvoicePaths();

        final Map<PdfConstants, Object> datasource = new EnumMap<>(PdfConstants.class);
        datasource.put(PdfConstants.INVOICE_NUM, "INV-1001");
        datasource.put(PdfConstants.CUSTOMER_NAME, "Bhaskar");
        datasource.put(PdfConstants.EMAIL, "bhaskar@test.com");
        datasource.put(PdfConstants.AMOUNT, BigDecimal.TEN);
        datasource.put(PdfConstants.PAYMENT_METHOD, "UPI");
        datasource.put(PdfConstants.PAYMENT_DATE, LocalDateTime.now().toString());
        // Remove one mandatory field for this iteration
        datasource.remove(missingKey);

        // When / Then
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class,
                        () -> pdfService.generateInvoicePdf(datasource));
        assertEquals(
                "Missing required PDF field: " + missingKey.name(),
                exception.getMessage()
        );
    }

    /**
     * Stubs all configuration paths required for successful PDF generation.
     *
     * <p>
     * This helper method centralizes configuration stubbing and is used only
     * by tests that execute the full JasperReports pipeline.
     * </p>
     */
    private void stubAllInvoicePaths() {
        when(invoiceProperties.jasperTemplatePath())
                .thenReturn("reports/invoice_template.jrxml");
        when(invoiceProperties.companyLogoPath())
                .thenReturn("/reports/accenture-logo.png");
        when(invoiceProperties.userLogoPath())
                .thenReturn("/reports/icon-user.png");
        when(invoiceProperties.emailLogoPath())
                .thenReturn("/reports/icon-email.png");
        when(invoiceProperties.amountLogoPath())
                .thenReturn("/reports/icon-amount.png");
        when(invoiceProperties.paymentLogoPath())
                .thenReturn("/reports/icon-payment.png");
        when(invoiceProperties.calendarLogoPath())
                .thenReturn("/reports/icon-calendar.png");
    }
}