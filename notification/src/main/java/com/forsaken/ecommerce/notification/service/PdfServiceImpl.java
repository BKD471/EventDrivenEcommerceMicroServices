package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.models.PdfConstants;
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
public class PdfServiceImpl implements IPdfService {

    @Override
    public byte[] generateAndSendInvoice(Map<PdfConstants, Object> datasource) throws JRException, IOException {
        log.info("Generate and send Invoice data");
        final InputStream jasperStream = new ClassPathResource("reports/invoice_template.jrxml").getInputStream();
        final JasperReport jasperReport = JasperCompileManager.compileReport(jasperStream);
        log.info("Jasper Report generated");

        final Map<String, Object> datasourceMap = new HashMap<>();
        datasourceMap.put("invoiceNumber", datasource.get(PdfConstants.INVOICE_NUM));
        datasourceMap.put("customerName", datasource.get(PdfConstants.CUSTOMER_NAME));
        datasourceMap.put("email", datasource.get(PdfConstants.EMAIL));
        datasourceMap.put("amount", datasource.get(PdfConstants.AMOUNT));
        datasourceMap.put("paymentMethod", datasource.get(PdfConstants.PAYMENT_METHOD));
        datasourceMap.put("paymentDate", datasource.get(PdfConstants.PAYMENT_DATE));
        datasourceMap.put("LOGO", getClass().getResourceAsStream("/reports/accenture-logo.png"));
        datasourceMap.put("ICON_USER", getClass().getResourceAsStream("/reports/icon-user.png"));
        datasourceMap.put("ICON_EMAIL", getClass().getResourceAsStream("/reports/icon-email.png"));
        datasourceMap.put("ICON_AMOUNT", getClass().getResourceAsStream("/reports/icon-amount.png"));
        datasourceMap.put("ICON_PAYMENT", getClass().getResourceAsStream("/reports/icon-payment.png"));
        datasourceMap.put("ICON_CALENDAR", getClass().getResourceAsStream("/reports/icon-calendar.png"));

        final JasperPrint print =
                JasperFillManager.fillReport(jasperReport, datasourceMap, new JREmptyDataSource());
        log.info("Print {}", print);
        return JasperExportManager.exportReportToPdf(print);
    }
}
