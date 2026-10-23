package com.forsaken.ecommerce.product.repository;

import com.forsaken.ecommerce.product.model.ReservationOrder;
import com.forsaken.ecommerce.product.model.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IReservationOrderRepository extends JpaRepository<ReservationOrder, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationOrder r where r.orderReference = :orderReference")
    Optional<ReservationOrder> findByOrderReferenceForUpdate(@Param("orderReference") String orderReference);

    /** Keyset page of RESERVED order references created before the cutoff. */
    @Query("select r.orderReference from ReservationOrder r where r.status = :status "
            + "and r.createdAt < :cutoff and r.confirmedAt is null and r.orderReference > :after order by r.orderReference asc")
    List<String> findReferencesByStatusCreatedBefore(
            @Param("status") ReservationStatus status,
            @Param("cutoff") Instant cutoff,
            @Param("after") String after,
            Pageable pageable);

    /** Keyset page of order references whose reservation was released before the cutoff. */
    @Query("select r.orderReference from ReservationOrder r where r.status = :status "
            + "and r.releasedAt < :cutoff and r.orderReference > :after order by r.orderReference asc")
    List<String> findReferencesByStatusReleasedBefore(
            @Param("status") ReservationStatus status,
            @Param("cutoff") Instant cutoff,
            @Param("after") String after,
            Pageable pageable);

    @Transactional
    @Modifying
    @Query("update ReservationOrder r set r.confirmedAt = :now "
            + "where r.orderReference = :orderReference and r.status = com.forsaken.ecommerce.product.model.ReservationStatus.RESERVED")
    int markConfirmed(@Param("orderReference") String orderReference, @Param("now") Instant now);
}
