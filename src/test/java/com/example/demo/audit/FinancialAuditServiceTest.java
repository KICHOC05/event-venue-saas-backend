package com.example.demo.audit;

import com.example.demo.audit.dto.FinancialAuditEntryResponse;
import com.example.demo.audit.service.FinancialAuditService;
import com.example.demo.branch.model.Branch;
import com.example.demo.cash.model.CashMovement;
import com.example.demo.cash.model.CashRegister;
import com.example.demo.cash.repository.CashMovementRepository;
import com.example.demo.common.enums.CashMovementType;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.common.enums.UserRole;
import com.example.demo.event.model.EventBooking;
import com.example.demo.event.model.EventPayment;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.order.model.Order;
import com.example.demo.payment.model.Payment;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.user.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialAuditServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private EventPaymentRepository eventPaymentRepository;
    @Mock private CashMovementRepository cashMovementRepository;

    private FinancialAuditService service;
    private Tenant tenant;
    private Branch branch;
    private User user;
    private CashRegister cash;

    @BeforeEach
    void setUp() {
        service = new FinancialAuditService(
                paymentRepository, eventPaymentRepository, cashMovementRepository);
        tenant = new Tenant(); tenant.setId(10L);
        branch = new Branch(); branch.setId(20L); branch.setPublicId("branch-a");
        branch.setName("Centro"); branch.setTenant(tenant);
        user = new User(); user.setId(30L); user.setPublicId("user-a");
        user.setName("Christopher"); user.setEmail("chris@example.com");
        user.setTenant(tenant); user.setBranch(branch);
        cash = new CashRegister(); cash.setId(40L); cash.setPublicId("cash-a");
        cash.setTenant(tenant); cash.setBranch(branch);
        TenantContext.set(new TenantContext.TenantInfo(10L, 20L, 30L, UserRole.ADMIN));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void unifiesPosEventAndMovementWithoutDuplicatingSemantics() {
        Payment pos = posPayment(1L, LocalDateTime.of(2026, 8, 14, 9, 30));
        EventPayment event = eventPayment(LocalDateTime.of(2026, 8, 14, 10, 20));
        CashMovement movement = movement(LocalDateTime.of(2026, 8, 14, 11, 0));

        when(paymentRepository.findForAudit(eq(10L), any(), any(), any(), any(), any(), any()))
                .thenReturn(page(List.of(pos)));
        when(eventPaymentRepository.findForAudit(eq(10L), any(), any(), any(), any(), any(), any()))
                .thenReturn(page(List.of(event)));
        when(cashMovementRepository.findForAudit(eq(10L), any(), any(), any(), any(), any()))
                .thenReturn(page(List.of(movement)));

        Page<FinancialAuditEntryResponse> result = service.getAudit(
                0, 20, null, null, null, null,
                LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 14));

        assertEquals(3, result.getTotalElements());
        assertEquals(List.of("MOVEMENT", "EVENT", "POS"),
                result.getContent().stream().map(FinancialAuditEntryResponse::getSource).toList());
        assertEquals(new BigDecimal("-200.00"), result.getContent().getFirst().getAmount());
        assertNull(result.getContent().getFirst().getPaymentMethod());
        assertEquals("EV-000034", result.getContent().get(1).getReference());
        assertEquals("Orden #000125", result.getContent().get(2).getReference());
    }

    @Test
    void appliesWholeDayBoundariesAndTenantToEverySource() {
        when(paymentRepository.findForAudit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
        when(eventPaymentRepository.findForAudit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
        when(cashMovementRepository.findForAudit(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        service.getAudit(0, 20, null, null, "user-a", "branch-a",
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 8));

        verify(paymentRepository).findForAudit(
                eq(10L), eq("branch-a"), eq(null), eq("user-a"),
                eq(LocalDateTime.of(2026, 8, 3, 0, 0)),
                eq(LocalDateTime.of(2026, 8, 9, 0, 0)), any());
        verify(eventPaymentRepository).findForAudit(
                eq(10L), eq("branch-a"), eq(null), eq("user-a"),
                eq(LocalDateTime.of(2026, 8, 3, 0, 0)),
                eq(LocalDateTime.of(2026, 8, 9, 0, 0)), any());
        verify(cashMovementRepository).findForAudit(
                eq(10L), eq("branch-a"), eq("user-a"),
                eq(LocalDateTime.of(2026, 8, 3, 0, 0)),
                eq(LocalDateTime.of(2026, 8, 9, 0, 0)), any());
    }

    @Test
    void paymentMethodFilterDoesNotMisclassifyMovementsAsPayments() {
        when(paymentRepository.findForAudit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
        when(eventPaymentRepository.findForAudit(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        service.getAudit(0, 20, null, PaymentMethod.CASH,
                null, null, null, null);

        verify(cashMovementRepository, never()).findForAudit(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void paginatesAfterStableCrossSourceOrdering() {
        List<Payment> payments = java.util.stream.LongStream.rangeClosed(1, 25)
                .mapToObj(i -> posPayment(i,
                        LocalDateTime.of(2026, 8, 14, 12, 0).minusMinutes(i)))
                .toList();
        when(paymentRepository.findForAudit(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> new PageImpl<>(payments,
                        invocation.getArgument(6), 25));

        Page<FinancialAuditEntryResponse> result = service.getAudit(
                1, 20, com.example.demo.audit.dto.FinancialAuditSource.POS,
                null, null, null, null, null);

        assertEquals(5, result.getNumberOfElements());
        assertEquals(25, result.getTotalElements());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findForAudit(any(), any(), any(), any(), any(), any(), pageable.capture());
        assertEquals(40, pageable.getValue().getPageSize());
    }

    private Payment posPayment(long id, LocalDateTime date) {
        Order order = new Order(); order.setId(124L + id); order.setPublicId("order-" + id);
        order.setTenant(tenant); order.setBranch(branch); order.setUser(user);
        Payment payment = new Payment(); payment.setId(id); payment.setPublicId("pos-payment-" + id);
        payment.setTenant(tenant); payment.setBranch(branch); payment.setUser(user); payment.setOrder(order);
        payment.setAmount(new BigDecimal("500.00")); payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setCreatedAt(date); return payment;
    }

    private EventPayment eventPayment(LocalDateTime date) {
        EventBooking booking = new EventBooking(); booking.setPublicId("event-a"); booking.setEventNumber(34L);
        EventPayment payment = new EventPayment(); payment.setId(2L); payment.setPublicId("event-payment-a");
        payment.setTenant(tenant); payment.setBranch(branch); payment.setCashRegister(cash);
        payment.setEventBooking(booking); payment.setAmount(new BigDecimal("1300.00"));
        payment.setPaymentMethod(PaymentMethod.TRANSFER); payment.setPaidAt(date);
        payment.setReceivedByUserPublicId(user.getPublicId());
        payment.setReceivedByUserEmail(user.getEmail()); return payment;
    }

    private CashMovement movement(LocalDateTime date) {
        CashMovement movement = new CashMovement(); movement.setId(3L); movement.setPublicId("movement-a");
        movement.setTenant(tenant); movement.setBranch(branch); movement.setCashRegister(cash); movement.setUser(user);
        movement.setType(CashMovementType.WITHDRAWAL); movement.setAmount(new BigDecimal("200.00"));
        movement.setReason("Proveedor"); movement.setCreatedAt(date); return movement;
    }

    private <T> Page<T> page(List<T> content) {
        return new PageImpl<>(content, PageRequestFactory.first(content.size()), content.size());
    }

    private static final class PageRequestFactory {
        private static Pageable first(int size) {
            return org.springframework.data.domain.PageRequest.of(0, Math.max(size, 1));
        }
    }
}
