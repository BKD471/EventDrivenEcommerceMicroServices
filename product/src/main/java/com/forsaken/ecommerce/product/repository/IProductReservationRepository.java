package com.forsaken.ecommerce.product.repository;

import com.forsaken.ecommerce.product.model.ProductReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IProductReservationRepository extends JpaRepository<ProductReservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProductReservation> findAllByOrderReferenceOrderByProductId(String orderReference);

    @Modifying
    @Query("delete from ProductReservation r where r.orderReference = :orderReference")
    int deleteAllByOrderReference(@Param("orderReference") String orderReference);
}
