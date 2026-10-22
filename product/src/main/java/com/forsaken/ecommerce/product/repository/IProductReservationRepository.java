package com.forsaken.ecommerce.product.repository;

import com.forsaken.ecommerce.product.model.ProductReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IProductReservationRepository extends JpaRepository<ProductReservation, Long> {

    List<ProductReservation> findAllByOrderReferenceOrderByProductId(String orderReference);
}
