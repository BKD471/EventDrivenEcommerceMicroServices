package com.forsaken.ecommerce.order.order.repository;

import com.forsaken.ecommerce.order.order.model.OrderDailySales;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface IOrderDailySalesRepository extends JpaRepository<OrderDailySales, Long> {

    List<OrderDailySales> findBySalesDateBetweenOrderBySalesDateAscPaymentMethodAsc(LocalDate from, LocalDate to);

    /** Executes immediately so a day can be regenerated inside one transaction without unique-key clashes. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OrderDailySales s where s.salesDate = :date")
    int deleteBySalesDate(@Param("date") LocalDate date);
}
