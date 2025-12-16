package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.configs.invoice.InvoiceProperties;
import com.forsaken.ecommerce.notification.models.PaymentMethod;
import com.forsaken.ecommerce.notification.models.PdfConstants;
import com.forsaken.ecommerce.notification.models.Product;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JRException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


import static com.forsaken.ecommerce.notification.models.EmailTemplates.ORDER_CONFIRMATION;
import static com.forsaken.ecommerce.notification.models.EmailTemplates.PAYMENT_CONFIRMATION;
import static java.nio.charset.StandardCharsets.UTF_8;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements IEmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final IPdfService pdfService;
    private final IS3Service s3Service;
    private final InvoiceProperties invoiceProperties;

    @Override
    @Async
    public void sendPaymentSuccessEmail(
            final String destinationEmail,
            final String customerName,
            final BigDecimal amount,
            final String orderReference,
            final PaymentMethod paymentMethod,
            final LocalDateTime paymentDate
    ) {
        log.info("Payment Success email to: {}", destinationEmail);

        final String templateName = PAYMENT_CONFIRMATION.getTemplate();
        final String invoiceNumber = UUID.randomUUID().toString();

        final Map<PdfConstants, Object> dataSource = new HashMap<>();
        dataSource.put(PdfConstants.INVOICE_NUM, invoiceNumber);
        dataSource.put(PdfConstants.CUSTOMER_NAME, customerName);
        dataSource.put(PdfConstants.EMAIL, destinationEmail);
        dataSource.put(PdfConstants.AMOUNT, amount);
        dataSource.put(PdfConstants.PAYMENT_METHOD, paymentMethod.toString());
        dataSource.put(PdfConstants.PAYMENT_DATE, getPaymentDate(paymentDate));

        try {
            final MimeMessage mimeMessage = mailSender.createMimeMessage();
            final MimeMessageHelper messageHelper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED,
                    UTF_8.name()
            );
            messageHelper.setFrom(invoiceProperties.senderEmailAddress());
            messageHelper.setSubject(PAYMENT_CONFIRMATION.getSubject());
            log.info("Sending email to: {}", destinationEmail);

            final byte[] pdfBytes = pdfService.generateInvoicePdf(dataSource);
            final String key = s3Service.uploadInvoice(pdfBytes, invoiceNumber);
            final URL presignedUrl = s3Service.generatePresignedUrl(key);

            final Map<String, Object> variables = new HashMap<>();
            variables.put("customerName", customerName);
            variables.put("amount", amount);
            variables.put("orderReference", orderReference);
            variables.put("downloadUrl", presignedUrl.toString());

            final Context context = new Context();
            context.setVariables(variables);

            final String htmlTemplate = templateEngine.process(templateName, context);
            messageHelper.setText(htmlTemplate, true);
            messageHelper.setTo(destinationEmail);

            final ByteArrayDataSource pdfDataSource = new ByteArrayDataSource(pdfBytes, "application/pdf");
            messageHelper.addAttachment("Invoice_" + invoiceNumber + ".pdf", pdfDataSource);

            mailSender.send(mimeMessage);
            log.info("Payment Email successfully sent to {} with template {} ", destinationEmail, templateName);
        } catch (MessagingException | MailException | JRException | IOException e) {
            log.warn("Cannot send Payment Email to {} ", destinationEmail, e);
        }
    }


    @Override
    @Async
    public void sendOrderConfirmationEmail(
            final String destinationEmail,
            final String customerName,
            final BigDecimal amount,
            final String orderReference,
            final List<Product> productList
    ) {
        log.info("Order success email to: {}", destinationEmail);
        final Map<String, Object> variables = new HashMap<>();
        variables.put("customerName", customerName);
        variables.put("totalAmount", amount);
        variables.put("orderReference", orderReference);
        variables.put("products", productList);

        final Context context = new Context();
        context.setVariables(variables);

        try {
            final MimeMessage mimeMessage = mailSender.createMimeMessage();
            final MimeMessageHelper messageHelper = new MimeMessageHelper(
                    mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, UTF_8.name());
            messageHelper.setFrom(invoiceProperties.senderEmailAddress());
            messageHelper.setSubject(ORDER_CONFIRMATION.getSubject());

            final String templateName = ORDER_CONFIRMATION.getTemplate();
            String htmlTemplate = templateEngine.process(templateName, context);
            messageHelper.setText(htmlTemplate, true);
            messageHelper.setTo(destinationEmail);
            mailSender.send(mimeMessage);
            log.info("Order Email successfully sent to {} with template {} ", destinationEmail, templateName);
        } catch (MessagingException | MailException e) {
            log.warn("Cannot send Order Email to {} ", destinationEmail, e);
        }
    }

    private String getPaymentDate(final LocalDateTime paymentDate) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        return paymentDate.format(formatter);
    }
}
