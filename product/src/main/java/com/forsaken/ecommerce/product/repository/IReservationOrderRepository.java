package com.forsaken.ecommerce.product.repository;

import com.forsaken.ecommerce.product.model.ReservationOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IReservationOrderRepository extends JpaRepository<ReservationOrder, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationOrder r where r.orderReference = :orderReference")
    Optional<ReservationOrder> findByOrderReferenceForUpdate(@Param("orderReference") String orderReference);
}
