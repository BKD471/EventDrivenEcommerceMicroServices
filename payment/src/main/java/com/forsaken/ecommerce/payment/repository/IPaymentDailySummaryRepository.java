package com.forsaken.ecommerce.payment.repository;

import com.forsaken.ecommerce.payment.model.PaymentDailySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface IPaymentDailySummaryRepository extends JpaRepository<PaymentDailySummary, Long> {

    List<PaymentDailySummary> findBySummaryDateBetweenOrderBySummaryDateAscPaymentMethodAsc(
            LocalDate from, LocalDate to);

    /** Executes immediately so a day can be regenerated inside one transaction without unique-key clashes. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PaymentDailySummary s where s.summaryDate = :date")
    int deleteBySummaryDate(@Param("date") LocalDate date);
}
