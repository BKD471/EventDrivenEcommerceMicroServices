package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.notification.configs.kafka.IdempotencyScope;
import com.forsaken.ecommerce.notification.configs.kafka.IdempotencyStore;
import com.forsaken.ecommerce.notification.configs.kafka.KafkaProperties;
import com.forsaken.ecommerce.notification.mapper.AvroMapper;
import com.forsaken.ecommerce.notification.models.Notification;
import com.forsaken.ecommerce.notification.repository.INotificationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.forsaken.ecommerce.notification.mapper.AvroMapper.fromBytes;
import static com.forsaken.ecommerce.notification.mapper.AvroMapper.getCustomerName;
import static com.forsaken.ecommerce.notification.mapper.AvroMapper.mapPaymentMethod;
import static com.forsaken.ecommerce.notification.mapper.AvroMapper.mapToOrderConfirmation;
import static com.forsaken.ecommerce.notification.mapper.AvroMapper.mapToPaymentConfirmation;
import static com.forsaken.ecommerce.notification.models.EventType.ORDER;
import static com.forsaken.ecommerce.notification.models.EventType.PAYMENT;
import static com.forsaken.ecommerce.notification.models.NotificationType.ORDER_CONFIRMATION;
import static com.forsaken.ecommerce.notification.models.NotificationType.PAYMENT_CONFIRMATION;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationConsumerImpl implements INotificationConsumer {

    private final INotificationRepository notificationRepository;
    private final IEmailService emailService;
    private final IDlqS3Service dlqS3Service;
    private final KafkaProperties kafkaProperties;
    private final IdempotencyStore idempotencyStore;
    private ZoneId zoneId;

    @KafkaListener(
            topics = "${spring.kafka.consumer.paymentTopicName}",
            groupId = "${spring.kafka.consumer.paymentGroupId}",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    @Override
    public void consumePaymentSuccessNotifications(
            final ConsumerRecord<String, PaymentConfirmation> record,
            final Acknowledgment acknowledgment
    ) {
        log.info("#Consuming the message from Payment Topic:: {}", getTimeStampForLogs(record));
        PaymentConfirmation paymentConfirmation = null;
        try {
            MDC.put("traceId", record.value() != null ? record.value().getTraceId() : "null record");
            MDC.put("spanId", "-");

            paymentConfirmation = record.value();
            // Null values can occur due to tombstone records or producer bugs.
            // These are non-recoverable and should be acknowledged to avoid retry loops.
            if (null == paymentConfirmation) {
                log.warn("Received null PaymentConfirmation in Kafka record, skipping processing.");
                acknowledgment.acknowledge();
                return;
            }

            final String eventId = paymentConfirmation.getOrderReference();
            final boolean firstTime = idempotencyStore.markIfNotProcessed(
                    IdempotencyScope.PAYMENT,
                    eventId
            );
            //  Avoid duplicate email
            if (!firstTime) {
                log.info("Duplicate payment event skipped: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Received PaymentConfirmation: {}", paymentConfirmation.getOrderReference());
            // send payment email
            emailService.sendPaymentSuccessEmail(
                    paymentConfirmation.getCustomerEmail(),
                    getCustomerName(paymentConfirmation),
                    fromBytes(paymentConfirmation.getAmount()),
                    paymentConfirmation.getOrderReference(),
                    mapPaymentMethod(paymentConfirmation.getPaymentMethod()),
                    LocalDateTime.ofInstant(
                            paymentConfirmation.getPaymentDate(),
                            ZoneId.of(kafkaProperties.timeZone())
                    )
            );
            // save to DB
            notificationRepository.save(
                    Notification.builder()
                            .type(PAYMENT_CONFIRMATION)
                            .notificationDate(LocalDateTime.now(ZoneId.of(kafkaProperties.timeZone())))
                            .paymentConfirmation(mapToPaymentConfirmation(paymentConfirmation))
                            .build()
            );

            acknowledgment.acknowledge();
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
            if (null != paymentConfirmation) {
                idempotencyStore.remove(
                        IdempotencyScope.PAYMENT,
                        paymentConfirmation.getOrderReference()
                );
            }
            throw new RuntimeException("Payment notification processing failed", ex);
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
            final ConsumerRecord<String, OrderConfirmation> record,
            final Acknowledgment acknowledgment
    ) {
        log.info("#Consuming the message from Order Topic:: {}", getTimeStampForLogs(record));
        OrderConfirmation orderConfirmation = null;
        try {
            MDC.put("traceId", record.value() != null ? record.value().getTraceId() : "null record");
            MDC.put("spanId", "-");

            orderConfirmation = record.value();
            // Null values can occur due to tombstone records or producer bugs.
            // These are non-recoverable and should be acknowledged to avoid retry loops.
            if (null == orderConfirmation) {
                log.warn("Received null OrderConfirmation in Kafka record, skipping processing.");
                acknowledgment.acknowledge();
                return;
            }
            final String eventId = orderConfirmation.getOrderReference();
            final boolean firstTime = idempotencyStore.markIfNotProcessed(
                    IdempotencyScope.ORDER,
                    eventId
            );
            //  Avoid duplicate email
            if (!firstTime) {
                log.info("Duplicate order event skipped: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Received OrderConfirmation: {}", orderConfirmation.getOrderReference());
            // send order email
            emailService.sendOrderConfirmationEmail(
                    orderConfirmation.getCustomer().getEmail(),
                    getCustomerName(orderConfirmation),
                    fromBytes(orderConfirmation.getTotalAmount()),
                    orderConfirmation.getOrderReference(),
                    orderConfirmation.getProducts().stream().map(AvroMapper::toProduct).toList()
            );
            // save to DB
            notificationRepository.save(
                    Notification.builder()
                            .type(ORDER_CONFIRMATION)
                            .notificationDate(LocalDateTime.now(ZoneId.of(kafkaProperties.timeZone())))
                            .orderConfirmation(mapToOrderConfirmation(orderConfirmation))
                            .build()
            );

            acknowledgment.acknowledge();
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
            idempotencyStore.remove(
                    IdempotencyScope.ORDER,
                    null != orderConfirmation ? orderConfirmation.getOrderReference() : "unknown"
            );
            throw new RuntimeException("Order notification processing failed", ex);
        } finally {
            MDC.clear();
        }
    }


    @KafkaListener(
            topics = "${spring.kafka.dlq.paymentDlqTopicName}",
            groupId = "${spring.kafka.dlq.groupId}",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    @Override
    public void consumePaymentDlqMessages(
            final ConsumerRecord<String, PaymentConfirmation> record,
            final Acknowledgment acknowledgment
    ) {
        log.warn("Payment DLQ EVENT RECEIVED for key={}, partition={}, offset={}, timestamp={}",
                record.key(), record.partition(), record.offset(), getTimeStampForLogs(record));
        try {
            dlqS3Service.storeToS3(record, PAYMENT);
            // Acknowledge only after successful persistence to avoid losing DLQ records
            acknowledgment.acknowledge();
        } catch (IOException ex) {
            log.error(
                    "Failed to persist Payment DLQ message to S3 for key={}, offset={}, partition={}, timestamp={}",
                    record.key(),
                    record.offset(),
                    record.partition(),
                    getTimeStampForLogs(record),
                    ex
            );
        }
    }


    @KafkaListener(
            topics = "${spring.kafka.dlq.orderDlqTopicName}",
            groupId = "${spring.kafka.dlq.groupId}",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    @Override
    public void consumeOrderDlqMessages(
            final ConsumerRecord<String, OrderConfirmation> record,
            final Acknowledgment acknowledgment
    ) {
        log.warn("Order DLQ EVENT RECEIVED for key={}, partition={}, offset={}, timestamp={}",
                record.key(), record.partition(), record.offset(), getTimeStampForLogs(record));
        try {
            dlqS3Service.storeToS3(record, ORDER);
            // Acknowledge only after successful persistence to avoid losing DLQ records
            acknowledgment.acknowledge();
        } catch (IOException ex) {
            log.error(
                    "Failed to persist Order DLQ message to S3 for key={}, offset={}, partition={}, timestamp={}",
                    record.key(),
                    record.offset(),
                    record.partition(),
                    getTimeStampForLogs(record),
                    ex
            );
        }
    }


    private LocalDateTime getTimeStampForLogs(final ConsumerRecord<String, ?> record) {
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(record.timestamp()),
                zoneId
        );
    }

    @PostConstruct
    void init() {
        this.zoneId = ZoneId.of(kafkaProperties.timeZone());
    }
}