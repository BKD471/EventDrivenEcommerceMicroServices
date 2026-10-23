package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.order.order.dto.OrderDailySalesDto;
import com.forsaken.ecommerce.order.order.model.OrderDailySales;
import com.forsaken.ecommerce.order.order.repository.IOrderDailySalesRepository;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import com.forsaken.ecommerce.order.order.repository.OrderSalesSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Builds and serves the daily COMPLETED-order sales report. Regenerating a day replaces it, so runs are idempotent. */
@Service
@RequiredArgsConstructor
public class OrderReportService {

    private final IOrderRepository orderRepository;
    private final IOrderDailySalesRepository salesRepository;

    @Transactional
    public int reportDay(final LocalDate date) {
        final LocalDateTime now = LocalDateTime.now();
        final List<OrderDailySales> rows = orderRepository
                .summarizeCompletedBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream()
                .map(summary -> toRow(date, summary, now))
                .toList();
        salesRepository.deleteBySalesDate(date);
        salesRepository.saveAll(rows);
        return rows.size();
    }

    @Transactional(readOnly = true)
    public List<OrderDailySalesDto> findDailySales(final LocalDate from, final LocalDate to) {
        return salesRepository.findBySalesDateBetweenOrderBySalesDateAscPaymentMethodAsc(from, to)
                .stream()
                .map(OrderDailySalesDto::from)
                .toList();
    }

    private static OrderDailySales toRow(
            final LocalDate date, final OrderSalesSummary summary, final LocalDateTime now) {
        return OrderDailySales.builder()
                .salesDate(date)
                .paymentMethod(summary.getPaymentMethod())
                .orderCount(summary.getCount())
                .totalAmount(summary.getTotalAmount() == null ? BigDecimal.ZERO : summary.getTotalAmount())
                .generatedAt(now)
                .build();
    }
}
