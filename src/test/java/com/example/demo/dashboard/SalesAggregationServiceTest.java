package com.example.demo.dashboard;

import com.example.demo.dashboard.service.SalesAggregationService;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesAggregationServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private EventPaymentRepository eventPaymentRepository;

    @InjectMocks private SalesAggregationService service;

    private final LocalDateTime start = LocalDate.of(2026, 8, 1).atStartOfDay();
    private final LocalDateTime end = LocalDate.of(2026, 8, 8).atStartOfDay();

    @Test
    void totalsCombinePosAndEventPaymentsExactlyOnce() {
        when(paymentRepository.sumTotalPaymentsByTenantInPeriod(10L, start, end))
                .thenReturn(bd("1250"));
        when(eventPaymentRepository.sumTotalByTenantInPeriod(10L, start, end))
                .thenReturn(bd("750"));

        SalesAggregationService.SourceTotals totals = service.totals(10L, start, end);

        assertAmount("1250", totals.pos());
        assertAmount("750", totals.events());
        assertAmount("2000", totals.total());
    }

    @Test
    void dailyTotalsMergeBothSourcesOnTheSameDate() {
        LocalDate augustFirst = LocalDate.of(2026, 8, 1);
        LocalDate augustSecond = LocalDate.of(2026, 8, 2);
        when(paymentRepository.dailySalesByTenantInPeriod(10L, start, end))
                .thenReturn(List.<Object[]>of(
                        new Object[]{Date.valueOf(augustFirst), bd("100")},
                        new Object[]{Date.valueOf(augustSecond), bd("200")}));
        when(eventPaymentRepository.dailySalesByTenantInPeriod(10L, start, end))
                .thenReturn(List.<Object[]>of(
                        new Object[]{Date.valueOf(augustFirst), bd("300")}));

        Map<LocalDate, BigDecimal> totals = service.dailyTotals(10L, start, end);

        assertAmount("400", totals.get(augustFirst));
        assertAmount("200", totals.get(augustSecond));
        assertEquals(2, totals.size());
    }

    @Test
    void paymentBreakdownCombinesEachMethodIndependently() {
        when(paymentRepository.paymentBreakdownByTenantInPeriod(10L, start, end))
                .thenReturn(List.<Object[]>of(new Object[]{bd("100"), bd("200"), bd("300")}));
        when(eventPaymentRepository.paymentBreakdownByTenantInPeriod(10L, start, end))
                .thenReturn(List.<Object[]>of(new Object[]{bd("400"), bd("500"), bd("600")}));

        SalesAggregationService.PaymentMethodTotals totals =
                service.paymentMethodTotals(10L, start, end);

        assertAmount("500", totals.cash());
        assertAmount("700", totals.card());
        assertAmount("900", totals.transfer());
    }

    @Test
    void nullRepositoryValuesAreNormalizedToZero() {
        when(paymentRepository.sumTotalPaymentsByTenantInPeriod(10L, start, end))
                .thenReturn(null);
        when(eventPaymentRepository.sumTotalByTenantInPeriod(10L, start, end))
                .thenReturn(null);
        when(eventPaymentRepository.countPaidEventsByTenantInPeriod(10L, start, end))
                .thenReturn(null);

        assertAmount("0", service.totals(10L, start, end).total());
        assertEquals(0L, service.countPaidEvents(10L, start, end));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual));
    }
}
