package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.models.PdfConstants;
import net.sf.jasperreports.engine.JRException;

import java.io.IOException;
import java.util.Map;

/**
 * Contract for generating PDF documents related to payments and invoices.
 * <p>
 * Implementations of this interface are responsible for:
 * <ul>
 *     <li>Preparing invoice data from the provided datasource</li>
 *     <li>Generating a PDF document (typically using a reporting engine such as JasperReports)</li>
 *     <li>Returning the generated PDF as a byte array for further processing
 *     (e.g. email attachment, file storage, or HTTP response)</li>
 * </ul>
 * </p>
 *
 * <p>
 * This interface defines only the business contract. The actual implementation
 * may involve template loading, parameter mapping, resource resolution
 * (logos/icons), and PDF rendering logic.
 * </p>
 *
 * <p><b>Thread Safety:</b><br>
 * Implementations should be stateless or thread-safe, as they are typically
 * used as Spring-managed singleton beans.
 * </p>
 */
public interface IPdfService {

    /**
     * Generates a payment invoice PDF using the provided datasource
     * and returns the resulting document as a byte array.
     *
     * <p>
     * The datasource map must contain all required values needed to populate
     * the invoice template. Keys are defined using {@link PdfConstants} to ensure
     * type safety and consistency.
     * </p>
     *
     * @param datasource a map containing invoice-related data such as
     *                   invoice number, customer details, payment amount,
     *                   payment method, and payment date
     * @return a byte array representing the generated PDF document
     * @throws JRException if an error occurs while compiling or filling
     *                     the report template
     * @throws IOException if an error occurs while loading templates
     *                     or static resources (e.g. images)
     */
    byte[] generateAndSendInvoice(final Map<PdfConstants, Object> datasource)
            throws JRException, IOException;
}
