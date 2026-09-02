package com.example.demo.dashboard.service;

import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SalesAggregationService {

    private final PaymentRepository paymentRepository;
    private final EventPaymentRepository eventPaymentRepository;

    public SourceTotals totals(
            Long tenantId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {

        BigDecimal pos = safe(paymentRepository.sumTotalPaymentsByTenantInPeriod(
                tenantId, startInclusive, endExclusive));
        BigDecimal events = safe(eventPaymentRepository.sumTotalByTenantInPeriod(
                tenantId, startInclusive, endExclusive));

        return new SourceTotals(pos, events);
    }

    public Map<LocalDate, BigDecimal> dailyTotals(
            Long tenantId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {

        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        mergeDailyRows(totals, paymentRepository.dailySalesByTenantInPeriod(
                tenantId, startInclusive, endExclusive));
        mergeDailyRows(totals, eventPaymentRepository.dailySalesByTenantInPeriod(
                tenantId, startInclusive, endExclusive));
        return totals;
    }

    public PaymentMethodTotals paymentMethodTotals(
            Long tenantId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {

        PaymentMethodTotals pos = readPaymentMethodTotals(
                paymentRepository.paymentBreakdownByTenantInPeriod(
                        tenantId, startInclusive, endExclusive));
        PaymentMethodTotals events = readPaymentMethodTotals(
                eventPaymentRepository.paymentBreakdownByTenantInPeriod(
                        tenantId, startInclusive, endExclusive));

        return pos.add(events);
    }

    public long countPaidEvents(
            Long tenantId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {

        Long count = eventPaymentRepository.countPaidEventsByTenantInPeriod(
                tenantId, startInclusive, endExclusive);
        return count != null ? count : 0L;
    }

    private void mergeDailyRows(Map<LocalDate, BigDecimal> totals, List<Object[]> rows) {
        if (rows == null) {
            return;
        }

        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            LocalDate date = parseDate(row[0]);
            totals.merge(date, toBigDecimal(row[1]), BigDecimal::add);
        }
    }

    private PaymentMethodTotals readPaymentMethodTotals(List<Object[]> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return PaymentMethodTotals.zero();
        }

        Object[] row = rows.get(0);
        return new PaymentMethodTotals(
                row.length > 0 ? toBigDecimal(row[0]) : BigDecimal.ZERO,
                row.length > 1 ? toBigDecimal(row[1]) : BigDecimal.ZERO,
                row.length > 2 ? toBigDecimal(row[2]) : BigDecimal.ZERO);
    }

    private LocalDate parseDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    public record SourceTotals(BigDecimal pos, BigDecimal events) {
        public BigDecimal total() {
            return pos.add(events);
        }
    }

    public record PaymentMethodTotals(
            BigDecimal cash,
            BigDecimal card,
            BigDecimal transfer) {

        public static PaymentMethodTotals zero() {
            return new PaymentMethodTotals(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public PaymentMethodTotals add(PaymentMethodTotals other) {
            return new PaymentMethodTotals(
                    cash.add(other.cash),
                    card.add(other.card),
                    transfer.add(other.transfer));
        }
    }
}
