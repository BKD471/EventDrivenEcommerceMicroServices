package com.forsaken.ecommerce.payment.service;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxMaintenanceServiceTest {

    @Mock
    private IPaymentOutboxRepository repository;

    @InjectMocks
    private PaymentOutboxMaintenanceService service;

    private PaymentOutboxEvent quarantined(final BigDecimal amount) {
        final Payment payment = Payment.builder()
                .orderReference("ORD-1")
                .amount(amount)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .customerFirstname("John")
                .customerLastname("Doe")
                .customerEmail("john@test.com")
                .build();
        final PaymentOutboxEvent event = new PaymentOutboxEvent(payment, Instant.now(), "trace");
        event.recordFailure(Instant.now());
        event.quarantine();
        return event;
    }

    @Test
    void shouldRequeueQuarantinedEventWhenPayloadIsNowValid() {
        final PaymentOutboxEvent event = quarantined(new BigDecimal("10.00"));
        when(repository.findById(1L)).thenReturn(Optional.of(event));

        assertThat(service.requeueQuarantined(1L)).isTrue();

        assertThat(event.getStatus()).isEqualTo(PaymentOutboxEvent.OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getNextAttemptAt()).isNull();
        verify(repository).save(event);
    }

    @Test
    void shouldKeepEventQuarantinedWhenPayloadStillCannotBeEncoded() {
        final PaymentOutboxEvent event = quarantined(new BigDecimal("10.123"));
        when(repository.findById(2L)).thenReturn(Optional.of(event));

        assertThat(service.requeueQuarantined(2L)).isFalse();

        assertThat(event.getStatus()).isEqualTo(PaymentOutboxEvent.OutboxStatus.QUARANTINED);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldIgnoreEventsThatAreNoLongerQuarantined() {
        final PaymentOutboxEvent event = quarantined(new BigDecimal("10.00"));
        event.requeue();
        when(repository.findById(3L)).thenReturn(Optional.of(event));

        assertThat(service.requeueQuarantined(3L)).isFalse();
        assertThat(service.requeueQuarantined(4L)).isFalse();
    }

    @Test
    void shouldPurgeOnlySentEventsAndSkipEmptyChunks() {
        when(repository.deleteByIdsAndStatus(List.of(1L, 2L), PaymentOutboxEvent.OutboxStatus.SENT)).thenReturn(2);

        assertThat(service.purgeSent(List.of(1L, 2L))).isEqualTo(2);
        assertThat(service.purgeSent(List.of())).isZero();
        verify(repository, never()).deleteByIdsAndStatus(List.of(), PaymentOutboxEvent.OutboxStatus.SENT);
    }
}
