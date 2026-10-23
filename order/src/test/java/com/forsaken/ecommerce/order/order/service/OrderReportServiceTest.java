package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.order.order.model.OrderDailySales;
import com.forsaken.ecommerce.order.order.model.PaymentMethod;
import com.forsaken.ecommerce.order.order.repository.IOrderDailySalesRepository;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import com.forsaken.ecommerce.order.order.repository.OrderSalesSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderReportServiceTest {

    private final IOrderRepository orders = mock(IOrderRepository.class);
    private final IOrderDailySalesRepository sales = mock(IOrderDailySalesRepository.class);
    private final OrderReportService service = new OrderReportService(orders, sales);

    @Test
    @SuppressWarnings("unchecked")
    void reportDayReplacesTheDayWithFreshAggregates() {
        final LocalDate day = LocalDate.of(2026, 1, 5);
        final OrderSalesSummary summary = mock(OrderSalesSummary.class);
        when(summary.getPaymentMethod()).thenReturn(PaymentMethod.values()[0]);
        when(summary.getCount()).thenReturn(4L);
        when(summary.getTotalAmount()).thenReturn(new BigDecimal("80.00"));
        when(orders.summarizeCompletedBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(summary));

        assertEquals(1, service.reportDay(day));

        final InOrder order = inOrder(sales);
        order.verify(sales).deleteBySalesDate(day);
        final ArgumentCaptor<List<OrderDailySales>> captor = ArgumentCaptor.forClass(List.class);
        order.verify(sales).saveAll(captor.capture());
        final OrderDailySales saved = captor.getValue().get(0);
        assertEquals(day, saved.getSalesDate());
        assertEquals(4, saved.getOrderCount());
        assertEquals(new BigDecimal("80.00"), saved.getTotalAmount());
    }

    @Test
    void reportDayWithNoOrdersClearsTheDay() {
        final LocalDate day = LocalDate.of(2026, 1, 5);
        when(orders.summarizeCompletedBetween(any(), any())).thenReturn(List.of());

        assertEquals(0, service.reportDay(day));
        verify(sales).deleteBySalesDate(day);
    }
}
