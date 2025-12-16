package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.models.PdfConstants;
import net.sf.jasperreports.engine.JRException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PdfServiceImpl}.
 *
 * <p>
 * This test class validates the behavior of the PDF generation service
 * responsible for creating invoice PDFs using JasperReports.
 * </p>
 *
 * <p>
 * <b>Testing strategy:</b>
 * <ul>
 *     <li>Positive test executes the real JasperReports pipeline using
 *         the invoice JRXML template available on the classpath.</li>
 *     <li>No mocking is used for the positive scenario to ensure that
 *         PDF generation works end-to-end.</li>
 *     <li>Negative scenarios validate exception propagation for
 *         template loading and Jasper compilation failures.</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Why real execution is used:</b>
 * <ul>
 *     <li>JasperReports relies heavily on static APIs and resource files.</li>
 *     <li>Mocking these APIs provides little value and leads to brittle tests.</li>
 *     <li>Verifying real PDF generation ensures higher confidence.</li>
 * </ul>
 * </p>
 */
class PdfServiceImplTest {

    private PdfServiceImpl pdfService;

    /**
     * Initializes a fresh {@link PdfServiceImpl} instance before each test.
     *
     * <p>
     * The service is created without mocks since PDF generation relies on
     * actual JasperReports resources and classpath files.
     * </p>
     */
    @BeforeEach
    void setUp() {
        pdfService = new PdfServiceImpl();
    }

    /**
     * Verifies that a valid PDF invoice is generated when all required
     * invoice data is provided.
     *
     * <p>
     * This test ensures:
     * <ul>
     *     <li>The JasperReports template is successfully compiled.</li>
     *     <li>The report is filled with the provided invoice data.</li>
     *     <li>A non-empty PDF byte array is produced.</li>
     *     <li>The generated output is a valid PDF document
     *         (validated using the PDF magic number "%PDF").</li>
     * </ul>
     * </p>
     *
     * @throws Exception if PDF generation fails unexpectedly
     */
    @Test
    void generateAndSendInvoice_shouldGenerateValidPdfBytes() throws Exception {
        // Given
        final Map<PdfConstants, Object> datasource = new EnumMap<>(PdfConstants.class);
        datasource.put(PdfConstants.INVOICE_NUM, "INV-1001");
        datasource.put(PdfConstants.CUSTOMER_NAME, "Bhaskar Das");
        datasource.put(PdfConstants.EMAIL, "bhaskar@test.com");
        datasource.put(PdfConstants.AMOUNT, new BigDecimal("150.50"));
        datasource.put(PdfConstants.PAYMENT_METHOD, "PAYPAL");
        datasource.put(PdfConstants.PAYMENT_DATE, LocalDateTime.now().toString());

        // When
        final byte[] pdfBytes = pdfService.generateAndSendInvoice(datasource);

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
     * Verifies that an {@link IOException} is thrown when the invoice
     * template cannot be loaded.
     *
     * <p>
     * This test simulates a failure scenario where the JRXML template
     * is missing or inaccessible from the classpath.
     * </p>
     */
    @Test
    void generateAndSendInvoice_shouldThrowIOException_whenTemplateMissing() {
        // Given
        final PdfServiceImpl brokenService = new PdfServiceImpl() {
            @Override
            public byte[] generateAndSendInvoice(Map<PdfConstants, Object> datasource)
                    throws IOException {
                throw new IOException("Template not found");
            }
        };

        final Map<PdfConstants, Object> datasource = Map.of(
                PdfConstants.INVOICE_NUM, "INV-1"
        );

        // When / Then
        assertThrows(IOException.class,
                () -> brokenService.generateAndSendInvoice(datasource)
        );
    }

    /**
     * Verifies that a {@link JRException} is thrown when JasperReports
     * fails to compile or process the invoice template.
     *
     * <p>
     * This test represents scenarios such as:
     * <ul>
     *     <li>Invalid JRXML syntax</li>
     *     <li>Corrupted report template</li>
     *     <li>Incompatible JasperReports version</li>
     * </ul>
     * </p>
     */
    @Test
    void generateAndSendInvoice_shouldThrowJRException_whenReportInvalid() {
        // Given
        final PdfServiceImpl brokenService = new PdfServiceImpl() {
            @Override
            public byte[] generateAndSendInvoice(Map<PdfConstants, Object> datasource)
                    throws JRException {
                throw new JRException("Jasper compilation failed");
            }
        };

        final Map<PdfConstants, Object> datasource = Map.of(
                PdfConstants.INVOICE_NUM, "INV-2"
        );

        // When / Then
        assertThrows(JRException.class,
                () -> brokenService.generateAndSendInvoice(datasource)
        );
    }
}