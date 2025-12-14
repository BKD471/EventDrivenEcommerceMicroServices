package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.notification.mapper.AvroMapper;
import com.forsaken.ecommerce.notification.models.Notification;
import com.forsaken.ecommerce.notification.repository.INotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.forsaken.ecommerce.notification.mapper.AvroMapper.fromBytes;
import static com.forsaken.ecommerce.notification.mapper.AvroMapper.mapPaymentMethod;
import static com.forsaken.ecommerce.notification.mapper.AvroMapper.mapToOrderConfirmation;
import static com.forsaken.ecommerce.notification.mapper.AvroMapper.mapToPaymentConfirmation;
import static com.forsaken.ecommerce.notification.models.NotificationType.ORDER_CONFIRMATION;
import static com.forsaken.ecommerce.notification.models.NotificationType.PAYMENT_CONFIRMATION;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationConsumerImpl implements INotificationConsumer {

    private final INotificationRepository notificationRepository;
    private final IEmailService emailService;

    @KafkaListener(
            topics = "${spring.kafka.consumer.paymentTopicName}",
            groupId = "${spring.kafka.consumer.paymentGroupId}",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    @Override
    public void consumePaymentSuccessNotifications(final ConsumerRecord<String, PaymentConfirmation> record) throws MessagingException {
        log.info("Consuming the message from payment-topic Topic:: {}", record.timestamp());

        try {
            final PaymentConfirmation paymentConfirmation = record.value();
            final String traceId = paymentConfirmation.getTraceId();
            MDC.put("traceId", traceId);
            MDC.put("spanId", "-");
            log.info("Received PaymentConfirmation: {}", paymentConfirmation.getOrderReference());
            notificationRepository.save(
                    Notification.builder()
                            .type(PAYMENT_CONFIRMATION)
                            .notificationDate(LocalDateTime.now())
                            .paymentConfirmation(mapToPaymentConfirmation(paymentConfirmation))
                            .build()
            );
            final var customerName = paymentConfirmation.getCustomerFirstname() + " " + paymentConfirmation.getCustomerLastname();
            final BigDecimal amount = fromBytes(paymentConfirmation.getAmount());
            emailService.sendPaymentSuccessEmail(
                    paymentConfirmation.getCustomerEmail(),
                    customerName,
                    amount,
                    paymentConfirmation.getOrderReference(),
                    mapPaymentMethod(paymentConfirmation.getPaymentMethod()),
                    instantToLocalDateTime(paymentConfirmation.getPaymentDate())
            );
            log.info("PaymentConfirmation has been sent successfully");
        } catch (Exception ex) {
            log.error("Failed to send email for {}. Triggering retry...", record.value().getCustomerEmail(), ex);
            throw new RuntimeException(ex);
        }
    }

    @KafkaListener(
            topics = "${spring.kafka.consumer.orderTopicName}",
            groupId = "${spring.kafka.consumer.orderGroupId}",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    @Override
    public void consumeOrderConfirmationNotifications(ConsumerRecord<String, OrderConfirmation> record) throws MessagingException {
        log.info("Consuming the message from order-topic Topic:: %s", record.timestamp());

        try {
            final OrderConfirmation orderConfirmation = record.value();
            final String traceId = orderConfirmation.getTraceId();
            MDC.put("traceId", traceId);
            MDC.put("spanId", "-");
            log.info("Received OrderConfirmation: {}", orderConfirmation.getOrderReference());
            notificationRepository.save(
                    Notification.builder()
                            .type(ORDER_CONFIRMATION)
                            .notificationDate(LocalDateTime.now())
                            .orderConfirmation(mapToOrderConfirmation(orderConfirmation))
                            .build()
            );
            final var customerName = orderConfirmation.getCustomer().getFirstname() + " " + orderConfirmation.getCustomer().getLastname();
            emailService.sendOrderConfirmationEmail(
                    orderConfirmation.getCustomer().getEmail(),
                    customerName,
                    fromBytes(orderConfirmation.getTotalAmount()),
                    orderConfirmation.getOrderReference(),
                    orderConfirmation.getProducts().stream().map(AvroMapper::toProduct).toList()
            );
            log.info("OrderConfirmation has been sent successfully");
        } catch (Exception ex) {
            log.error("Failed to send email for {}. Triggering retry...", record.value().getCustomer().getEmail(), ex);
            throw new RuntimeException(ex);
        }
    }

    private static LocalDateTime instantToLocalDateTime(final Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
