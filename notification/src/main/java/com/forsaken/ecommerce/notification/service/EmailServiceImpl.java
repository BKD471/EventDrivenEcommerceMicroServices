package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.models.PaymentMethod;
import com.forsaken.ecommerce.notification.models.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements IEmailService {

    @Override
    @Async
    public void sendPaymentSuccessEmail(
            final String destinationEmail,
            final String customerName,
            final BigDecimal amount,
            final String orderReference,
            final PaymentMethod paymentMethod,
            final LocalDateTime paymentDate
    ) throws MessagingException {
        // TODO: Implement email sending logic using JavaMailSender or any other email service
    }

    @Override
    @Async
    public void sendOrderConfirmationEmail(
            final String destinationEmail,
            final String customerName,
            final BigDecimal amount,
            final String orderReference,
            final List<Product> productList
    ) throws MessagingException {
        // TODO: Implement email sending logic using JavaMailSender or any other email service
    }
}
