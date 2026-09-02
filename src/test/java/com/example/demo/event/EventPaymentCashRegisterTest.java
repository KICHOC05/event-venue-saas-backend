package com.example.demo.event;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.cash.model.CashRegister;
import com.example.demo.cash.repository.CashRegisterRepository;
import com.example.demo.common.enums.CashStatus;
import com.example.demo.common.enums.EventStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.common.enums.ProductType;
import com.example.demo.common.enums.UserRole;
import com.example.demo.document.service.DocumentSequenceService;
import com.example.demo.event.dto.CreateEventRequest;
import com.example.demo.event.dto.RegisterEventPaymentRequest;
import com.example.demo.event.model.EventBooking;
import com.example.demo.event.model.EventPayment;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.event.repository.EventRescheduleHistoryRepository;
import com.example.demo.event.service.EventService;
import com.example.demo.product.model.Product;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventPaymentCashRegisterTest {

    @Mock private EventBookingRepository eventBookingRepository;
    @Mock private EventPaymentRepository eventPaymentRepository;
    @Mock private EventRescheduleHistoryRepository eventRescheduleHistoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private CashRegisterRepository cashRegisterRepository;

    @InjectMocks private EventService eventService;

    private Tenant tenant;
    private Branch branch;
    private Product product;
    private CashRegister cash;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(10L);
        tenant.setBusinessName("Tenant A");

        branch = new Branch();
        branch.setId(20L);
        branch.setTenant(tenant);
        branch.setName("Sucursal A");

        product = new Product();
        product.setId(30L);
        product.setPublicId("package-1");
        product.setTenant(tenant);
        product.setName("Paquete Premium");
        product.setPrice(bd("6500"));
        product.setType(ProductType.PACKAGE);
        product.setActive(true);
        product.setDepartment("Eventos");

        cash = new CashRegister();
        cash.setId(45L);
        cash.setTenant(tenant);
        cash.setBranch(branch);
        cash.setStatus(CashStatus.OPEN);
        cash.setOpenedAt(LocalDateTime.now());

        TenantContext.set(new TenantContext.TenantInfo(10L, 20L, null, UserRole.ADMIN));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void initialPaymentIsAssignedToTheOpenCashRegister() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(branchRepository.findByIdAndTenant_Id(20L, 10L)).thenReturn(Optional.of(branch));
        when(productRepository.findByPublicIdAndTenant_IdAndActiveTrue("package-1", 10L))
                .thenReturn(Optional.of(product));
        when(cashRegisterRepository.findByTenant_IdAndBranch_IdAndStatusForUpdate(
                10L, 20L, CashStatus.OPEN)).thenReturn(Optional.of(cash));
        when(documentSequenceService.nextNumber(any(), any(), any())).thenReturn(125L);
        when(eventBookingRepository.save(any(EventBooking.class))).thenAnswer(invocation -> {
            EventBooking event = invocation.getArgument(0);
            event.setId(40L);
            event.setPublicId("event-1");
            event.setCreatedAt(LocalDateTime.now());
            event.setUpdatedAt(LocalDateTime.now());
            return event;
        });

        eventService.createEvent(createRequest());

        ArgumentCaptor<EventPayment> captor = ArgumentCaptor.forClass(EventPayment.class);
        verify(eventPaymentRepository).save(captor.capture());
        EventPayment payment = captor.getValue();
        assertSame(cash, payment.getCashRegister());
        assertAmount("1300", payment.getAmount());
        assertEquals(PaymentMethod.CASH, payment.getPaymentMethod());
    }

    @Test
    void laterPaymentIsAssignedToSameBranchOpenCashAndUpdatesEvent() {
        EventBooking event = event("event-1", branch);
        when(eventBookingRepository.findByPublicId("event-1")).thenReturn(Optional.of(event));
        when(cashRegisterRepository.findByTenant_IdAndBranch_IdAndStatusForUpdate(
                10L, 20L, CashStatus.OPEN)).thenReturn(Optional.of(cash));

        RegisterEventPaymentRequest request = RegisterEventPaymentRequest.builder()
                .amount(bd("2000"))
                .paymentMethod(PaymentMethod.TRANSFER)
                .reference("SPEI-TEST-2000")
                .build();
        var response = eventService.registerEventPayment("event-1", request);

        ArgumentCaptor<EventPayment> captor = ArgumentCaptor.forClass(EventPayment.class);
        verify(eventPaymentRepository).save(captor.capture());
        assertSame(cash, captor.getValue().getCashRegister());
        assertEquals(PaymentMethod.TRANSFER, captor.getValue().getPaymentMethod());
        assertAmount("3300", event.getDepositAmount());
        assertAmount("3200", event.getRemainingAmount());
        assertAmount("5200", response.getPreviousBalance());
        assertAmount("3300", response.getTotalPaid());
        assertAmount("3200", response.getRemainingAmount());
        assertEquals(false, response.getFullyPaid());
    }

    @Test
    void paymentFromAnotherBranchIsRejectedBeforeResolvingCash() {
        Branch otherBranch = new Branch();
        otherBranch.setId(99L);
        otherBranch.setTenant(tenant);
        otherBranch.setName("Sucursal B");
        EventBooking event = event("event-other-branch", otherBranch);
        when(eventBookingRepository.findByPublicId("event-other-branch"))
                .thenReturn(Optional.of(event));
        RegisterEventPaymentRequest request = RegisterEventPaymentRequest.builder()
                .amount(bd("100"))
                .paymentMethod(PaymentMethod.CASH)
                .build();

        assertThrows(SecurityException.class,
                () -> eventService.registerEventPayment("event-other-branch", request));
        verify(cashRegisterRepository, never())
                .findByTenant_IdAndBranch_IdAndStatusForUpdate(any(), any(), any());
        verify(eventPaymentRepository, never()).save(any());
    }

    @Test
    void paymentFromAnotherTenantIsRejectedBeforeResolvingCash() {
        Tenant otherTenant = new Tenant();
        otherTenant.setId(999L);
        Branch otherTenantBranch = new Branch();
        otherTenantBranch.setId(20L);
        otherTenantBranch.setTenant(otherTenant);
        otherTenantBranch.setName("Sucursal Tenant B");
        EventBooking event = event("event-other-tenant", otherTenantBranch);
        event.setTenant(otherTenant);
        when(eventBookingRepository.findByPublicId("event-other-tenant"))
                .thenReturn(Optional.of(event));
        RegisterEventPaymentRequest request = RegisterEventPaymentRequest.builder()
                .amount(bd("100"))
                .paymentMethod(PaymentMethod.CASH)
                .build();

        assertThrows(SecurityException.class,
                () -> eventService.registerEventPayment("event-other-tenant", request));
        verify(cashRegisterRepository, never())
                .findByTenant_IdAndBranch_IdAndStatusForUpdate(any(), any(), any());
        verify(eventPaymentRepository, never()).save(any());
    }

    @Test
    void paymentWithoutOpenCashIsRejectedAtomically() {
        EventBooking event = event("event-1", branch);
        when(eventBookingRepository.findByPublicId("event-1")).thenReturn(Optional.of(event));
        when(cashRegisterRepository.findByTenant_IdAndBranch_IdAndStatusForUpdate(
                10L, 20L, CashStatus.OPEN)).thenReturn(Optional.empty());
        RegisterEventPaymentRequest request = RegisterEventPaymentRequest.builder()
                .amount(bd("100"))
                .paymentMethod(PaymentMethod.CARD)
                .reference("AUT-TEST-100")
                .build();

        assertThrows(IllegalStateException.class,
                () -> eventService.registerEventPayment("event-1", request));
        verify(eventPaymentRepository, never()).save(any());
        assertAmount("1300", event.getDepositAmount());
    }

    private CreateEventRequest createRequest() {
        return CreateEventRequest.builder()
                .customerName("Cliente")
                .phone("7710000000")
                .childName("Niño")
                .childAge(7)
                .eventDate(LocalDate.now().plusDays(10))
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(19, 0))
                .guestChildren(10)
                .guestAdults(10)
                .packageProductPublicId("package-1")
                .depositAmount(bd("1300"))
                .initialPaymentMethod(PaymentMethod.CASH)
                .build();
    }

    private EventBooking event(String publicId, Branch eventBranch) {
        return EventBooking.builder()
                .id(40L)
                .publicId(publicId)
                .tenant(tenant)
                .branch(eventBranch)
                .eventNumber(125L)
                .packageProduct(product)
                .customerName("Cliente")
                .childName("Niño")
                .eventDate(LocalDate.now().plusDays(10))
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(19, 0))
                .eventPrice(bd("6500"))
                .depositAmount(bd("1300"))
                .remainingAmount(bd("5200"))
                .status(EventStatus.PENDING_DEPOSIT)
                .build();
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual));
    }
}
