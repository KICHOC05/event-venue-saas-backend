package com.example.demo.dashboard.service;

import com.example.demo.common.enums.EventStatus;
import com.example.demo.dashboard.dto.*;
import com.example.demo.dashboard.dto.DashboardResponse.InventorySummary;
import com.example.demo.dashboard.dto.DashboardResponse.LowStockProductDTO;
import com.example.demo.dashboard.dto.DashboardResponse.UpcomingEventDTO;
import com.example.demo.dashboard.dto.StatsResponse.PaymentBreakdown;
import com.example.demo.event.model.EventBooking;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.product.model.Product;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.security.TenantContext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

        private final SalesAggregationService salesAggregationService;
        private final OrderRepository orderRepository;
        private final OrderItemRepository orderItemRepository;
        private final ProductRepository productRepository;
        private final EventBookingRepository eventBookingRepository;
        private final EventPaymentRepository eventPaymentRepository;

        private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        private static final int LOW_STOCK_THRESHOLD = 5;
        private static final int UPCOMING_EVENTS_LIMIT = 5;
        private static final List<EventStatus> UPCOMING_EVENT_STATUSES = List.of(
                        EventStatus.PENDING_DEPOSIT,
                        EventStatus.CONFIRMED,
                        EventStatus.IN_PROGRESS);

        public DashboardResponse getDashboard() {

                Long tenantId = TenantContext.getTenantId();
                LocalDate today = LocalDate.now();

                LocalDateTime startOfDay = today.atStartOfDay();
                LocalDateTime endExclusive = today.plusDays(1).atStartOfDay();
                LocalDateTime endOfDay = endExclusive.minusNanos(1);

                LocalDate yesterday = today.minusDays(1);
                LocalDateTime startYesterday = yesterday.atStartOfDay();

                LocalDate firstDayOfMonth = today.withDayOfMonth(1);
                LocalDateTime startOfMonth = firstDayOfMonth.atStartOfDay();

                LocalDate firstDayPrevMonth = firstDayOfMonth.minusMonths(1);
                LocalDateTime startPrevMonth = firstDayPrevMonth.atStartOfDay();

                SalesAggregationService.SourceTotals todaySales = salesAggregationService.totals(
                                tenantId, startOfDay, endExclusive);
                SalesAggregationService.SourceTotals yesterdaySales = salesAggregationService.totals(
                                tenantId, startYesterday, startOfDay);
                BigDecimal salesToday = todaySales.total();
                BigDecimal salesYesterday = yesterdaySales.total();
                Double salesTodayGrowth = calculateGrowth(salesToday, salesYesterday);

                SalesAggregationService.SourceTotals monthSales = salesAggregationService.totals(
                                tenantId, startOfMonth, endExclusive);
                SalesAggregationService.SourceTotals previousMonthSales = salesAggregationService.totals(
                                tenantId, startPrevMonth, startOfMonth);
                BigDecimal monthlyRevenue = monthSales.total();
                BigDecimal prevMonthRevenue = previousMonthSales.total();
                Double monthlyGrowth = calculateGrowth(monthlyRevenue, prevMonthRevenue);

                InventorySummary inventory = buildInventorySummary(tenantId);

                SalesChartDTO salesChart = buildSalesChart(tenantId, 7);

                List<TopItemDTO> topPackages = mergeTopItems(
                                orderItemRepository.topPackagesByTenant(tenantId, startOfMonth, endOfDay),
                                eventPaymentRepository.paidPackagesByTenantInPeriod(
                                                tenantId, startOfMonth, endExclusive),
                                5);

                List<EventBooking> upcomingBookings = eventBookingRepository
                                .findByTenant_IdAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                                                tenantId, today, today.plusYears(1))
                                .stream()
                                .filter(e -> UPCOMING_EVENT_STATUSES.contains(e.getStatus()))
                                .collect(Collectors.toList());

                int scheduledEventsCount = upcomingBookings.size();

                List<UpcomingEventDTO> upcomingEvents = upcomingBookings.stream()
                                .limit(UPCOMING_EVENTS_LIMIT)
                                .map(this::toUpcomingEventDTO)
                                .collect(Collectors.toList());

                return DashboardResponse.builder()
                                .salesToday(salesToday)
                                .salesYesterday(salesYesterday)
                                .salesTodayGrowth(salesTodayGrowth)
                                .posSalesToday(todaySales.pos())
                                .eventSalesToday(todaySales.events())
                                .monthlyRevenue(monthlyRevenue)
                                .previousMonthRevenue(prevMonthRevenue)
                                .monthlyGrowth(monthlyGrowth)
                                .posMonthlyRevenue(monthSales.pos())
                                .eventMonthlyRevenue(monthSales.events())
                                .inventory(inventory)
                                .salesChart(salesChart)
                                .topPackages(topPackages)
                                .upcomingEvents(upcomingEvents)
                                .scheduledEventsCount(scheduledEventsCount)
                                .build();
        }

        public StatsResponse getStats(Integer rangeDays) {

                Long tenantId = TenantContext.getTenantId();

                if (rangeDays == null || rangeDays <= 0)
                        rangeDays = 7;

                LocalDate today = LocalDate.now();
                LocalDateTime endExclusive = today.plusDays(1).atStartOfDay();
                LocalDateTime endOfDay = endExclusive.minusNanos(1);
                LocalDate startDate = today.minusDays(rangeDays - 1);
                LocalDateTime start = startDate.atStartOfDay();

                LocalDate prevStartDate = startDate.minusDays(rangeDays);
                LocalDateTime prevStart = prevStartDate.atStartOfDay();

                SalesAggregationService.SourceTotals periodSales = salesAggregationService.totals(
                                tenantId, start, endExclusive);
                SalesAggregationService.SourceTotals previousPeriodSales = salesAggregationService.totals(
                                tenantId, prevStart, start);
                BigDecimal totalSales = periodSales.total();
                BigDecimal prevTotalSales = previousPeriodSales.total();
                Double growthPercentage = calculateGrowth(totalSales, prevTotalSales);

                Long totalOrders = orderRepository.countClosedOrdersByTenant(tenantId, start, endOfDay);
                if (totalOrders == null)
                        totalOrders = 0L;

                long paidEvents = salesAggregationService.countPaidEvents(
                                tenantId, start, endExclusive);
                long totalTransactions = totalOrders + paidEvents;

                BigDecimal averageTicket = BigDecimal.ZERO;
                if (totalTransactions > 0) {
                        averageTicket = totalSales.divide(
                                        BigDecimal.valueOf(totalTransactions), 2, RoundingMode.HALF_UP);
                }

                SalesChartDTO dailySales = buildSalesChart(tenantId, rangeDays);

                List<TopItemDTO> salesByProduct = buildTopItems(
                                orderItemRepository.topProductsByTenant(tenantId, start, endOfDay), null);

                List<TopItemDTO> salesByPackage = mergeTopItems(
                                orderItemRepository.topPackagesByTenant(tenantId, start, endOfDay),
                                eventPaymentRepository.paidPackagesByTenantInPeriod(
                                                tenantId, start, endExclusive),
                                null);

                List<TopItemDTO> topProducts = buildTopItems(
                                orderItemRepository.allItemsSoldByTenant(tenantId, start, endOfDay), 10);

                PaymentBreakdown paymentBreakdown = buildPaymentBreakdown(tenantId, start, endExclusive);

                return StatsResponse.builder()
                                .rangeDays(rangeDays)
                                .dateFrom(startDate.format(DATE_FMT))
                                .dateTo(today.format(DATE_FMT))
                                .dailySales(dailySales)
                                .salesByProduct(salesByProduct)
                                .salesByPackage(salesByPackage)
                                .topProducts(topProducts)
                                .totalSales(totalSales)
                                .posSales(periodSales.pos())
                                .eventSales(periodSales.events())
                                .averageTicket(averageTicket)
                                .growthPercentage(growthPercentage)
                                .totalOrders(totalOrders)
                                .paidEvents(paidEvents)
                                .totalTransactions(totalTransactions)
                                .scheduledEvents(Math.toIntExact(safeLong(
                                                eventBookingRepository.countScheduledByTenantAndEventDateBetween(
                                                                tenantId, startDate, today))))
                                .paymentBreakdown(paymentBreakdown)
                                .build();
        }

        private UpcomingEventDTO toUpcomingEventDTO(EventBooking event) {
                return UpcomingEventDTO.builder()
                                .date(event.getEventDate() != null ? event.getEventDate().format(DATE_FMT) : "")
                                .client(event.getCustomerName())
                                .packageName(event.getPackageProduct() != null
                                                ? event.getPackageProduct().getName()
                                                : "-")
                                .children(event.getGuestChildren() != null ? event.getGuestChildren() : 0)
                                .status(mapEventStatus(event.getStatus()))
                                .build();
        }

        private String mapEventStatus(EventStatus status) {
                if (status == null)
                        return "PENDING";
                // El badge del dashboard ya maneja CONFIRMED / PENDING / CANCELLED
                return status == EventStatus.PENDING_DEPOSIT ? "PENDING" : status.name();
        }

        private BigDecimal safe(BigDecimal value) {
                return value != null ? value : BigDecimal.ZERO;
        }

        private Double calculateGrowth(BigDecimal current, BigDecimal previous) {
                current = safe(current);
                previous = safe(previous);

                if (previous.compareTo(BigDecimal.ZERO) == 0) {
                        return current.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
                }

                return current.subtract(previous)
                                .divide(previous, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(1, RoundingMode.HALF_UP)
                                .doubleValue();
        }

        private InventorySummary buildInventorySummary(Long tenantId) {

                List<Product> products = productRepository.findAllByTenant_IdAndActiveTrue(tenantId);

                int totalProducts = products.size();

                int totalStock = products.stream()
                                .filter(p -> p.getStock() != null)
                                .mapToInt(Product::getStock)
                                .sum();

                List<LowStockProductDTO> lowStock = products.stream()
                                .filter(p -> p.getStock() != null
                                                && p.getStock() >= 0
                                                && p.getStock() <= LOW_STOCK_THRESHOLD)
                                .map(p -> LowStockProductDTO.builder()
                                                .publicId(p.getPublicId())
                                                .name(p.getName())
                                                .stock(p.getStock())
                                                .build())
                                .collect(Collectors.toList());

                return InventorySummary.builder()
                                .totalProducts(totalProducts)
                                .totalStock(totalStock)
                                .lowStockCount(lowStock.size())
                                .lowStockProducts(lowStock)
                                .build();
        }

        private SalesChartDTO buildSalesChart(Long tenantId, int days) {

                LocalDate today = LocalDate.now();
                LocalDate startDate = today.minusDays(days - 1);
                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime endExclusive = today.plusDays(1).atStartOfDay();

                Map<LocalDate, BigDecimal> salesMap = salesAggregationService.dailyTotals(
                                tenantId, start, endExclusive);

                List<String> labels = new ArrayList<>();
                List<BigDecimal> data = new ArrayList<>();
                List<String> fullDates = new ArrayList<>();

                Locale locale = Locale.of("es", "MX");

                for (int i = 0; i < days; i++) {
                        LocalDate date = startDate.plusDays(i);

                        String label;
                        if (days > 7) {
                                label = date.format(DateTimeFormatter.ofPattern("dd/MM"));
                        } else {
                                DayOfWeek dow = date.getDayOfWeek();
                                label = dow.getDisplayName(TextStyle.SHORT, locale);
                                label = label.substring(0, 1).toUpperCase() + label.substring(1);
                        }

                        labels.add(label);
                        data.add(salesMap.getOrDefault(date, BigDecimal.ZERO));
                        fullDates.add(date.format(DATE_FMT));
                }

                return SalesChartDTO.builder()
                                .labels(labels)
                                .data(data)
                                .fullDates(fullDates)
                                .build();
        }

        private List<TopItemDTO> buildTopItems(List<Object[]> raw, Integer limit) {

                List<TopItemDTO> items = new ArrayList<>();

                for (Object[] row : raw) {
                        items.add(TopItemDTO.builder()
                                        .publicId((String) row[0])
                                        .name((String) row[1])
                                        .quantitySold(((Number) row[2]).longValue())
                                        .totalRevenue(toBigDecimal(row[3]))
                                        .build());
                }

                if (limit != null && items.size() > limit) {
                        return items.subList(0, limit);
                }

                return items;
        }

        private List<TopItemDTO> mergeTopItems(
                        List<Object[]> firstSource,
                        List<Object[]> secondSource,
                        Integer limit) {

                Map<String, TopItemDTO> merged = new LinkedHashMap<>();
                mergeTopItemRows(merged, firstSource);
                mergeTopItemRows(merged, secondSource);

                List<TopItemDTO> items = new ArrayList<>(merged.values());
                items.sort(Comparator
                                .comparing(TopItemDTO::getQuantitySold, Comparator.reverseOrder())
                                .thenComparing(TopItemDTO::getTotalRevenue, Comparator.reverseOrder())
                                .thenComparing(TopItemDTO::getName));

                if (limit != null && items.size() > limit) {
                        return items.subList(0, limit);
                }
                return items;
        }

        private void mergeTopItemRows(Map<String, TopItemDTO> merged, List<Object[]> rows) {
                if (rows == null) {
                        return;
                }

                for (Object[] row : rows) {
                        String publicId = (String) row[0];
                        String name = (String) row[1];
                        long quantity = ((Number) row[2]).longValue();
                        BigDecimal revenue = toBigDecimal(row[3]);

                        merged.compute(publicId, (key, current) -> {
                                if (current == null) {
                                        return TopItemDTO.builder()
                                                        .publicId(publicId)
                                                        .name(name)
                                                        .quantitySold(quantity)
                                                        .totalRevenue(revenue)
                                                        .build();
                                }
                                current.setQuantitySold(current.getQuantitySold() + quantity);
                                current.setTotalRevenue(current.getTotalRevenue().add(revenue));
                                return current;
                        });
                }
        }

        private PaymentBreakdown buildPaymentBreakdown(
                        Long tenantId, LocalDateTime start, LocalDateTime end) {

                SalesAggregationService.PaymentMethodTotals totals =
                                salesAggregationService.paymentMethodTotals(tenantId, start, end);
                return PaymentBreakdown.builder()
                                .cashTotal(totals.cash())
                                .cardTotal(totals.card())
                                .transferTotal(totals.transfer())
                                .build();
        }

        private long safeLong(Long value) {
                return value != null ? value : 0L;
        }

        private BigDecimal toBigDecimal(Object value) {
                if (value == null)
                        return BigDecimal.ZERO;
                if (value instanceof BigDecimal)
                        return (BigDecimal) value;
                if (value instanceof Number)
                        return BigDecimal.valueOf(((Number) value).doubleValue());
                return new BigDecimal(value.toString());
        }
}
