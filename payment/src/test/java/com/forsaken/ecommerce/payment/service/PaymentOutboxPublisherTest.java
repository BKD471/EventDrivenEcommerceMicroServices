package com.forsaken.ecommerce.payment.service;

import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.avro.PaymentMethod;
import com.forsaken.ecommerce.payment.model.Payment;
import com.forsaken.ecommerce.payment.model.PaymentOutboxEvent;
import com.forsaken.ecommerce.payment.repository.IPaymentOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxPublisherTest {

    @Mock
    private IPaymentOutboxRepository outboxRepository;

    @Mock
    private INotificationProducerService notificationProducer;

    @InjectMocks
    private PaymentOutboxPublisher publisher;

    private PaymentOutboxEvent event() {
        return event(new BigDecimal("10.00"));
    }

    private PaymentOutboxEvent event(final BigDecimal amount) {
        final Payment payment = Payment.builder()
                .orderReference("order-1")
                .amount(amount)
                .paymentMethod(PaymentMethod.PAYPAL)
                .customerFirstname("John")
                .customerLastname("Doe")
                .customerEmail("john@doe.com")
                .build();
        return new PaymentOutboxEvent(payment, Instant.now(), "trace");
    }

    @Test
    void publishPending_ShouldSendAndMarkEventsSent() {
        final PaymentOutboxEvent event = event();
        when(outboxRepository.findReadyToPublish(
                any(), any(), any()))
                .thenReturn(List.of(event));

        publisher.publishPending();

        verify(notificationProducer).sendNotification(any(PaymentConfirmation.class));
        assertEquals(PaymentOutboxEvent.OutboxStatus.SENT, event.getStatus());
    }

    @Test
    void publishPending_ShouldKeepEventPending_WhenKafkaSendFails() {
        final PaymentOutboxEvent event = event();
        for (int i = 0; i < 10; i++) {
            event.recordFailure(Instant.now());
        }
        when(outboxRepository.findReadyToPublish(
                any(), any(), any()))
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("kafka down"))
                .when(notificationProducer).sendNotification(any(PaymentConfirmation.class));

        publisher.publishPending();

        assertEquals(PaymentOutboxEvent.OutboxStatus.PENDING, event.getStatus());
        assertEquals(11, event.getAttempts());
        assertTrue(event.getNextAttemptAt().isAfter(Instant.now()));
    }

    @Test
    void publishPending_ShouldQuarantineInvalidPayloadAndContinueWithOthers() {
        final PaymentOutboxEvent bad = event(new BigDecimal("10.001"));
        final PaymentOutboxEvent good = event();
        when(outboxRepository.findReadyToPublish(
                any(), any(), any()))
                .thenReturn(List.of(bad, good));

        publisher.publishPending();

        assertEquals(PaymentOutboxEvent.OutboxStatus.QUARANTINED, bad.getStatus());
        assertEquals(PaymentOutboxEvent.OutboxStatus.SENT, good.getStatus());
        verify(notificationProducer).sendNotification(any(PaymentConfirmation.class));
    }
}
