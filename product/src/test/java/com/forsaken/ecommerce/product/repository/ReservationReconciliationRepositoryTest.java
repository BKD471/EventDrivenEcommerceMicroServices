package com.forsaken.ecommerce.product.repository;

import com.forsaken.ecommerce.product.model.ReservationOrder;
import com.forsaken.ecommerce.product.model.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.flyway.enabled=false"
})
class ReservationReconciliationRepositoryTest {

    @Autowired
    private IReservationOrderRepository repository;

    @Test
    void findsOldReservedOrdersIncludingConfirmedReservations() {
        repository.save(new ReservationOrder("order-active", ReservationStatus.RESERVED));
        repository.markConfirmed("order-active", Instant.now());
        repository.save(new ReservationOrder("order-released", ReservationStatus.RELEASED));

        final var results = repository.findReservationsCreatedBefore(
                ReservationStatus.RESERVED,
                Instant.now().plusSeconds(1),
                "",
                PageRequest.of(0, 10));

        assertEquals(1, results.size());
        assertEquals("order-active", results.get(0).orderReference());
        assertNotNull(results.get(0).confirmedAt());
    }
}
