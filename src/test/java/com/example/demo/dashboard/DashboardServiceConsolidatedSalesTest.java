package com.example.demo.dashboard;

import com.example.demo.common.enums.UserRole;
import com.example.demo.dashboard.dto.StatsResponse;
import com.example.demo.dashboard.service.DashboardService;
import com.example.demo.dashboard.service.SalesAggregationService;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceConsolidatedSalesTest {

    @Mock private SalesAggregationService salesAggregationService;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private EventBookingRepository eventBookingRepository;
    @Mock private EventPaymentRepository eventPaymentRepository;

    @InjectMocks private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.TenantInfo(10L, 20L, 30L, UserRole.ADMIN));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void statsUseCombinedRevenueAndCombinedTransactionDenominator() {
        when(salesAggregationService.totals(eq(10L), any(), any()))
                .thenReturn(
                        new SalesAggregationService.SourceTotals(bd("1000"), bd("500")),
                        new SalesAggregationService.SourceTotals(bd("800"), bd("200")));
        when(salesAggregationService.countPaidEvents(eq(10L), any(), any()))
                .thenReturn(2L);
        when(salesAggregationService.dailyTotals(eq(10L), any(), any()))
                .thenReturn(Map.of());
        when(salesAggregationService.paymentMethodTotals(eq(10L), any(), any()))
                .thenReturn(SalesAggregationService.PaymentMethodTotals.zero());
        when(orderRepository.countClosedOrdersByTenant(eq(10L), any(), any()))
                .thenReturn(3L);
        when(orderItemRepository.topProductsByTenant(eq(10L), any(), any()))
                .thenReturn(Collections.emptyList());
        when(orderItemRepository.topPackagesByTenant(eq(10L), any(), any()))
                .thenReturn(Collections.emptyList());
        when(orderItemRepository.allItemsSoldByTenant(eq(10L), any(), any()))
                .thenReturn(Collections.emptyList());
        when(eventPaymentRepository.paidPackagesByTenantInPeriod(eq(10L), any(), any()))
                .thenReturn(Collections.emptyList());
        when(eventBookingRepository.countScheduledByTenantAndEventDateBetween(
                eq(10L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(1L);

        StatsResponse result = dashboardService.getStats(7);

        assertAmount("1500", result.getTotalSales());
        assertAmount("1000", result.getPosSales());
        assertAmount("500", result.getEventSales());
        assertAmount("300", result.getAverageTicket());
        assertEquals(3L, result.getTotalOrders());
        assertEquals(2L, result.getPaidEvents());
        assertEquals(5L, result.getTotalTransactions());
        assertEquals(50.0, result.getGrowthPercentage());
        assertEquals(1, result.getScheduledEvents());
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual));
    }
}
