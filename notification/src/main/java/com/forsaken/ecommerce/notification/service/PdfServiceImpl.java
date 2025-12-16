package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.configs.invoice.InvoiceProperties;
import com.forsaken.ecommerce.notification.models.PdfConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PdfServiceImpl implements IPdfService {

    private final InvoiceProperties invoiceProperties;

    @Override
    public byte[] generateInvoicePdf(final Map<PdfConstants, Object> datasource) throws JRException, IOException {
        log.info("Generate Invoice PDF data");

        final JasperReport jasperReport;
        try (
                final InputStream jasperStream =
                        new ClassPathResource(invoiceProperties.jasperTemplatePath()).getInputStream();
                final InputStream logo =
                        getClass().getResourceAsStream(invoiceProperties.companyLogoPath());
                final InputStream iconUser =
                        getClass().getResourceAsStream(invoiceProperties.userLogoPath());
                final InputStream iconEmail =
                        getClass().getResourceAsStream(invoiceProperties.emailLogoPath());
                final InputStream iconAmount =
                        getClass().getResourceAsStream(invoiceProperties.amountLogoPath());
                final InputStream iconPayment =
                        getClass().getResourceAsStream(invoiceProperties.paymentLogoPath());
                final InputStream iconCalendar =
                        getClass().getResourceAsStream(invoiceProperties.calendarLogoPath())
        ) {
            jasperReport = JasperCompileManager.compileReport(jasperStream);
            log.info("Jasper Report generated");

            final Map<String, Object> datasourceMap = new HashMap<>();
            datasourceMap.put("invoiceNumber", required(datasource, PdfConstants.INVOICE_NUM));
            datasourceMap.put("customerName", required(datasource, PdfConstants.CUSTOMER_NAME));
            datasourceMap.put("email", required(datasource, PdfConstants.EMAIL));
            datasourceMap.put("amount", required(datasource, PdfConstants.AMOUNT));
            datasourceMap.put("paymentMethod", required(datasource, PdfConstants.PAYMENT_METHOD));
            datasourceMap.put("paymentDate", required(datasource, PdfConstants.PAYMENT_DATE));

            datasourceMap.put("LOGO", logo);
            datasourceMap.put("ICON_USER", iconUser);
            datasourceMap.put("ICON_EMAIL", iconEmail);
            datasourceMap.put("ICON_AMOUNT", iconAmount);
            datasourceMap.put("ICON_PAYMENT", iconPayment);
            datasourceMap.put("ICON_CALENDAR", iconCalendar);

            final JasperPrint print =
                    JasperFillManager.fillReport(jasperReport, datasourceMap, new JREmptyDataSource());
            log.info("Print {}", print);
            return JasperExportManager.exportReportToPdf(print);
        }
    }

    private Object required(
            Map<PdfConstants, Object> datasource,
            PdfConstants key
    ) {
        final Object value = datasource.get(key);
        if (null == value) {
            throw new IllegalArgumentException(
                    "Missing required PDF field: " + key.name()
            );
        }
        return value;
    }
}
