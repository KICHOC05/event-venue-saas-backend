package com.example.demo.ticket;

import com.example.demo.branch.model.Branch;
import com.example.demo.common.enums.EventStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.common.enums.ProductType;
import com.example.demo.common.enums.TenantStatus;
import com.example.demo.common.enums.UserRole;
import com.example.demo.event.model.EventBooking;
import com.example.demo.event.model.EventPayment;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.product.model.Product;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.ticket.service.TicketService;
import jakarta.persistence.EntityNotFoundException;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceEventPaymentReceiptTest {

    private static final Long TENANT_ID = 10L;
    private static final String EVENT_PUBLIC_ID = "a3f2b1c4-event";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private EventBookingRepository eventBookingRepository;
    @Mock
    private EventPaymentRepository eventPaymentRepository;

    @InjectMocks
    private TicketService ticketService;

    private EventBooking event;
    private EventPayment firstPayment;
    private EventPayment secondPayment;
    private EventPayment finalPayment;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setBusinessName("Space & Kids");
        tenant.setPhone("771-000-0000");
        tenant.setWebsite("https://example.test/pagos?a=1&b=2");
        tenant.setLogoUrl("https://example.test/logo.png");
        tenant.setStatus(TenantStatus.ACTIVE);

        Branch branch = new Branch();
        branch.setId(20L);
        branch.setTenant(tenant);
        branch.setName("Sucursal Centro");
        branch.setAddress("Calle 1 <Centro>");
        branch.setPhone("771-111-1111");

        Product product = new Product();
        product.setId(30L);
        product.setTenant(tenant);
        product.setName("Fiesta Premium");
        product.setPrice(new BigDecimal("6500.00"));
        product.setType(ProductType.PACKAGE);
        product.setDepartment("Eventos");
        product.setActive(true);

        event = EventBooking.builder()
                .id(40L)
                .publicId(EVENT_PUBLIC_ID)
                .tenant(tenant)
                .branch(branch)
                .eventNumber(125L)
                .packageProduct(product)
                .customerName("Christopher <Cliente>")
                .phone("771-222-2222")
                .childName("Mateo & Familia")
                .childAge(7)
                .eventDate(LocalDate.of(2026, 8, 20))
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(19, 0))
                .eventPrice(new BigDecimal("6500.00"))
                .depositAmount(new BigDecimal("6500.00"))
                .remainingAmount(BigDecimal.ZERO)
                .status(EventStatus.CONFIRMED)
                .build();

        LocalDateTime firstPaidAt = LocalDateTime.of(2026, 8, 14, 0, 30);
        LocalDateTime sharedPaidAt = LocalDateTime.of(2026, 8, 14, 12, 35);
        firstPayment = payment(45L, "payment-1", "1300.00", PaymentMethod.CASH, firstPaidAt);
        secondPayment = payment(46L, "payment-2", "2000.00", PaymentMethod.TRANSFER, sharedPaidAt);
        secondPayment.setReference("SPEI-293829");
        secondPayment.setNotes("Segundo abono <confirmado>.");
        finalPayment = payment(47L, "payment-3", "3200.00", PaymentMethod.CARD, sharedPaidAt);

        TenantContext.set(new TenantContext.TenantInfo(TENANT_ID, 20L, 99L, UserRole.ADMIN));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void firstPaymentStartsFromFullEventBalance() {
        stubReceipt(firstPayment, List.of(firstPayment));

        String html = ticketService.generateEventPaymentReceipt(EVENT_PUBLIC_ID, firstPayment.getPublicId());

        assertReceiptBalances(html, "6500.00", "1300.00", "1300.00", "5200.00");
        assertTrue(html.contains("EV-000125"));
        assertTrue(html.contains("RP-000045"));
        assertTrue(html.contains("Christopher &lt;Cliente&gt;"));
        assertTrue(html.contains("Mateo &amp; Familia"));
        assertTrue(html.contains("https://api.qrserver.com/v1/create-qr-code/"));
    }

    @Test
    void secondPaymentUsesOnlyStrictlyPreviousPayments() {
        stubReceipt(secondPayment, List.of(firstPayment, secondPayment, finalPayment));

        String html = ticketService.generateEventPaymentReceipt(EVENT_PUBLIC_ID, secondPayment.getPublicId());

        assertReceiptBalances(html, "5200.00", "2000.00", "3300.00", "3200.00");
        assertTrue(html.contains("Transferencia"));
        assertTrue(html.contains("SPEI-293829"));
        assertTrue(html.contains("Segundo abono &lt;confirmado&gt;."));
        assertTrue(html.contains("caja@example.test"));
    }

    @Test
    void finalPaymentMarksEventAsPaidByThatPayment() {
        stubReceipt(finalPayment, List.of(firstPayment, secondPayment, finalPayment));

        String html = ticketService.generateEventPaymentReceipt(EVENT_PUBLIC_ID, finalPayment.getPublicId());

        assertReceiptBalances(html, "3200.00", "3200.00", "6500.00", "0.00");
        assertTrue(html.contains("EVENTO LIQUIDADO CON ESTE PAGO"));
    }

    @Test
    void historicalReprintDoesNotChangeAfterLaterPayments() {
        when(eventPaymentRepository.findByPublicIdAndEventBooking_PublicIdAndTenant_Id(
                firstPayment.getPublicId(), EVENT_PUBLIC_ID, TENANT_ID)).thenReturn(Optional.of(firstPayment));
        when(eventPaymentRepository.findByEventBooking_PublicIdAndTenant_IdOrderByPaidAtAscIdAsc(
                EVENT_PUBLIC_ID, TENANT_ID))
                .thenReturn(List.of(firstPayment))
                .thenReturn(List.of(firstPayment, secondPayment, finalPayment));

        String original = ticketService.generateEventPaymentReceipt(EVENT_PUBLIC_ID, firstPayment.getPublicId());
        event.setEventPrice(new BigDecimal("9999.00"));
        String reprint = ticketService.generateEventPaymentReceipt(EVENT_PUBLIC_ID, firstPayment.getPublicId());

        assertEquals(original, reprint);
        assertReceiptBalances(reprint, "6500.00", "1300.00", "1300.00", "5200.00");
    }

    @Test
    void wrongTenantCannotReadPaymentReceipt() {
        TenantContext.set(new TenantContext.TenantInfo(999L, 20L, 99L, UserRole.ADMIN));
        when(eventPaymentRepository.findByPublicIdAndEventBooking_PublicIdAndTenant_Id(
                firstPayment.getPublicId(), EVENT_PUBLIC_ID, 999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> ticketService.generateEventPaymentReceipt(EVENT_PUBLIC_ID, firstPayment.getPublicId()));
        verify(eventPaymentRepository).findByPublicIdAndEventBooking_PublicIdAndTenant_Id(
                firstPayment.getPublicId(), EVENT_PUBLIC_ID, 999L);
    }

    @Test
    void paymentFromAnotherEventCannotBeMixedIntoReceipt() {
        String otherEventPublicId = "other-event";
        when(eventPaymentRepository.findByPublicIdAndEventBooking_PublicIdAndTenant_Id(
                firstPayment.getPublicId(), otherEventPublicId, TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> ticketService.generateEventPaymentReceipt(otherEventPublicId, firstPayment.getPublicId()));
        verify(eventPaymentRepository).findByPublicIdAndEventBooking_PublicIdAndTenant_Id(
                firstPayment.getPublicId(), otherEventPublicId, TENANT_ID);
    }

    private EventPayment payment(Long id, String publicId, String amount,
                                 PaymentMethod method, LocalDateTime paidAt) {
        return EventPayment.builder()
                .id(id)
                .publicId(publicId)
                .eventBooking(event)
                .tenant(event.getTenant())
                .branch(event.getBranch())
                .amount(new BigDecimal(amount))
                .eventPriceAtPayment(new BigDecimal("6500.00"))
                .paymentMethod(method)
                .receivedByUserPublicId("user-99")
                .receivedByUserEmail("caja@example.test")
                .paidAt(paidAt)
                .build();
    }

    private void stubReceipt(EventPayment target, List<EventPayment> orderedPayments) {
        when(eventPaymentRepository.findByPublicIdAndEventBooking_PublicIdAndTenant_Id(
                target.getPublicId(), EVENT_PUBLIC_ID, TENANT_ID)).thenReturn(Optional.of(target));
        when(eventPaymentRepository.findByEventBooking_PublicIdAndTenant_IdOrderByPaidAtAscIdAsc(
                EVENT_PUBLIC_ID, TENANT_ID)).thenReturn(orderedPayments);
    }

    private void assertReceiptBalances(String html, String previous, String payment,
                                       String totalPaid, String newBalance) {
        assertTrue(html.contains("Saldo anterior:</span><span>$" + previous));
        assertTrue(html.contains("Este pago:</span><span>$" + payment));
        assertTrue(html.contains("Total pagado:</span><span>$" + totalPaid));
        assertTrue(html.contains("Saldo nuevo:</span><span>$" + newBalance));
    }
}
