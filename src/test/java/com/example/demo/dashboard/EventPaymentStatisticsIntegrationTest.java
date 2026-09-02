package com.example.demo.dashboard;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.common.enums.EventStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.common.enums.ProductType;
import com.example.demo.event.model.EventBooking;
import com.example.demo.event.model.EventPayment;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.product.model.Product;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:event-statistics-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class EventPaymentStatisticsIntegrationTest {

    @Autowired private EventPaymentRepository eventPaymentRepository;
    @Autowired private EventBookingRepository eventBookingRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        eventPaymentRepository.deleteAll();
        eventBookingRepository.deleteAll();
        productRepository.deleteAll();
        branchRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void eventAggregatesRespectTenantDatesMethodsAndDistinctBookings() {
        Tenant tenantA = createTenant("Tenant A");
        Branch branchA = createBranch(tenantA, "Sucursal A");
        Product packageA = createPackage(tenantA, "Paquete A");
        EventBooking firstEvent = createEvent(tenantA, branchA, packageA, 1L);
        EventBooking secondEvent = createEvent(tenantA, branchA, packageA, 2L);

        Tenant tenantB = createTenant("Tenant B");
        Branch branchB = createBranch(tenantB, "Sucursal B");
        Product packageB = createPackage(tenantB, "Paquete B");
        EventBooking otherTenantEvent = createEvent(tenantB, branchB, packageB, 1L);

        LocalDateTime start = LocalDate.of(2026, 8, 1).atStartOfDay();
        LocalDateTime endExclusive = LocalDate.of(2026, 8, 3).atStartOfDay();

        createPayment(firstEvent, tenantA, branchA, "100", PaymentMethod.CASH,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        createPayment(firstEvent, tenantA, branchA, "200", PaymentMethod.CARD,
                LocalDateTime.of(2026, 8, 2, 11, 0));
        createPayment(secondEvent, tenantA, branchA, "50", PaymentMethod.TRANSFER,
                LocalDateTime.of(2026, 8, 2, 12, 0));
        createPayment(secondEvent, tenantA, branchA, "999", PaymentMethod.CASH,
                endExclusive);
        createPayment(otherTenantEvent, tenantB, branchB, "1000", PaymentMethod.CASH,
                LocalDateTime.of(2026, 8, 1, 9, 0));

        assertAmount("350", eventPaymentRepository.sumTotalByTenantInPeriod(
                tenantA.getId(), start, endExclusive));
        assertEquals(2L, eventPaymentRepository.countPaidEventsByTenantInPeriod(
                tenantA.getId(), start, endExclusive));

        Object[] methods = eventPaymentRepository.paymentBreakdownByTenantInPeriod(
                tenantA.getId(), start, endExclusive).get(0);
        assertAmount("100", toBigDecimal(methods[0]));
        assertAmount("200", toBigDecimal(methods[1]));
        assertAmount("50", toBigDecimal(methods[2]));

        List<Object[]> daily = eventPaymentRepository.dailySalesByTenantInPeriod(
                tenantA.getId(), start, endExclusive);
        assertEquals(2, daily.size());
        assertAmount("100", toBigDecimal(daily.get(0)[1]));
        assertAmount("250", toBigDecimal(daily.get(1)[1]));

        Object[] packageStats = eventPaymentRepository.paidPackagesByTenantInPeriod(
                tenantA.getId(), start, endExclusive).get(0);
        assertEquals(packageA.getPublicId(), packageStats[0]);
        assertEquals(2L, ((Number) packageStats[2]).longValue());
        assertAmount("350", toBigDecimal(packageStats[3]));
    }

    private Tenant createTenant(String name) {
        Tenant tenant = new Tenant();
        tenant.setBusinessName(name);
        return tenantRepository.saveAndFlush(tenant);
    }

    private Branch createBranch(Tenant tenant, String name) {
        Branch branch = new Branch();
        branch.setTenant(tenant);
        branch.setName(name);
        return branchRepository.saveAndFlush(branch);
    }

    private Product createPackage(Tenant tenant, String name) {
        Product product = new Product();
        product.setTenant(tenant);
        product.setName(name);
        product.setPrice(bd("6500"));
        product.setType(ProductType.PACKAGE);
        product.setActive(true);
        product.setDepartment("Eventos");
        return productRepository.saveAndFlush(product);
    }

    private EventBooking createEvent(
            Tenant tenant,
            Branch branch,
            Product product,
            Long eventNumber) {
        EventBooking event = EventBooking.builder()
                .tenant(tenant)
                .branch(branch)
                .eventNumber(eventNumber)
                .packageProduct(product)
                .customerName("Cliente " + eventNumber)
                .childName("Niño " + eventNumber)
                .eventDate(LocalDate.of(2026, 9, 1).plusDays(eventNumber))
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(19, 0))
                .eventPrice(bd("6500"))
                .depositAmount(BigDecimal.ZERO)
                .remainingAmount(bd("6500"))
                .status(EventStatus.PENDING_DEPOSIT)
                .build();
        return eventBookingRepository.saveAndFlush(event);
    }

    private void createPayment(
            EventBooking event,
            Tenant tenant,
            Branch branch,
            String amount,
            PaymentMethod method,
            LocalDateTime paidAt) {
        EventPayment payment = EventPayment.builder()
                .eventBooking(event)
                .tenant(tenant)
                .branch(branch)
                .amount(bd(amount))
                .eventPriceAtPayment(event.getEventPrice())
                .paymentMethod(method)
                .paidAt(paidAt)
                .build();
        EventPayment saved = eventPaymentRepository.saveAndFlush(payment);
        jdbcTemplate.update(
                "UPDATE event_payments SET paid_at = ? WHERE id = ?",
                Timestamp.valueOf(paidAt),
                saved.getId());
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual));
    }
}
