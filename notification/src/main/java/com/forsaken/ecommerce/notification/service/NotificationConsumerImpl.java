package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.notification.configs.kafka.KafkaProperties;
import com.forsaken.ecommerce.notification.mapper.AvroMapper;
import com.forsaken.ecommerce.notification.models.Notification;
import com.forsaken.ecommerce.notification.repository.INotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.forsaken.ecommerce.notification.mapper.AvroMapper.fromBytes;
import static com.forsaken.ecommerce.notification.mapper.AvroMapper.getCustomerName;
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
    private final KafkaProperties kafkaProperties;

    @KafkaListener(
            topics = "${spring.kafka.consumer.paymentTopicName}",
            groupId = "${spring.kafka.consumer.paymentGroupId}",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    @Override
    public void consumePaymentSuccessNotifications(
            final ConsumerRecord<String, PaymentConfirmation> record
    ) {
        log.info("#Consuming the message from Payment Topic:: {}", getTimeStampForLogs(record));

        try {
            MDC.put("traceId", record.value() != null ? record.value().getTraceId() : "null record");
            MDC.put("spanId", "-");

            final PaymentConfirmation paymentConfirmation = record.value();
            if (null == paymentConfirmation) {
                log.warn("Received null PaymentConfirmation in Kafka record, skipping processing.");
                return;
            }

            log.info("Received PaymentConfirmation: {}", paymentConfirmation.getOrderReference());
            notificationRepository.save(
                    Notification.builder()
                            .type(PAYMENT_CONFIRMATION)
                            .notificationDate(LocalDateTime.now(ZoneId.of(kafkaProperties.timeZone())))
                            .paymentConfirmation(mapToPaymentConfirmation(paymentConfirmation))
                            .build()
            );
            final var customerName = getCustomerName(paymentConfirmation);
            final BigDecimal amount = fromBytes(paymentConfirmation.getAmount());
            emailService.sendPaymentSuccessEmail(
                    paymentConfirmation.getCustomerEmail(),
                    customerName,
                    amount,
                    paymentConfirmation.getOrderReference(),
                    mapPaymentMethod(paymentConfirmation.getPaymentMethod()),
                    LocalDateTime.ofInstant(
                            paymentConfirmation.getPaymentDate(),
                            ZoneId.of(kafkaProperties.timeZone())
                    )
            );
            log.info("PaymentConfirmation has been sent successfully: {}", getTimeStampForLogs(record));
        } catch (Exception ex) {
            log.error(
                    "Failed to send payment email notification. customerEmail={}, topic={}, partition={}, offset={}, timestamp={}",
                    record.value() != null && record.value().getCustomerEmail() != null
                            ? record.value().getCustomerEmail()
                            : "null record",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    getTimeStampForLogs(record),
                    ex
            );
        } finally {
            MDC.clear();
        }
    }


    @KafkaListener(
            topics = "${spring.kafka.consumer.orderTopicName}",
            groupId = "${spring.kafka.consumer.orderGroupId}",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    @Override
    public void consumeOrderConfirmationNotifications(
            final ConsumerRecord<String, OrderConfirmation> record
    ) {
        log.info("#Consuming the message from Order Topic:: {}", getTimeStampForLogs(record));

        try {
            MDC.put("traceId", record.value() != null ? record.value().getTraceId() : "null record");
            MDC.put("spanId", "-");

            final OrderConfirmation orderConfirmation = record.value();
            if (null == orderConfirmation) {
                log.warn("Received null OrderConfirmation in Kafka record, skipping processing.");
                return;
            }

            log.info("Received OrderConfirmation: {}", orderConfirmation.getOrderReference());
            notificationRepository.save(
                    Notification.builder()
                            .type(ORDER_CONFIRMATION)
                            .notificationDate(LocalDateTime.now(ZoneId.of(kafkaProperties.timeZone())))
                            .orderConfirmation(mapToOrderConfirmation(orderConfirmation))
                            .build()
            );
            final var customerName = getCustomerName(orderConfirmation);
            emailService.sendOrderConfirmationEmail(
                    orderConfirmation.getCustomer().getEmail(),
                    customerName,
                    fromBytes(orderConfirmation.getTotalAmount()),
                    orderConfirmation.getOrderReference(),
                    orderConfirmation.getProducts().stream().map(AvroMapper::toProduct).toList()
            );
            log.info("OrderConfirmation has been sent successfully: {}", getTimeStampForLogs(record));
        } catch (Exception ex) {
            log.error(
                    "Failed to send order email notification. customerEmail={}, topic={}, partition={}, offset={}, timestamp={}",
                    record.value() != null && record.value().getCustomer() != null
                            ? record.value().getCustomer().getEmail()
                            : "null record",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    getTimeStampForLogs(record),
                    ex
            );
        } finally {
            MDC.clear();
        }
    }

    private LocalDateTime getTimeStampForLogs(final ConsumerRecord<String, ?> record) {
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(record.timestamp()),
                ZoneId.of(kafkaProperties.timeZone())
        );
    }
}
