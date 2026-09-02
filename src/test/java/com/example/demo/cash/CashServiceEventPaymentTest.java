package com.example.demo.cash;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.cash.dto.CashMovementRequest;
import com.example.demo.cash.dto.CashRegisterResponse;
import com.example.demo.cash.dto.CloseCashRequest;
import com.example.demo.cash.model.CashMovement;
import com.example.demo.cash.model.CashRegister;
import com.example.demo.cash.repository.CashMovementRepository;
import com.example.demo.cash.repository.CashRegisterRepository;
import com.example.demo.cash.service.CashService;
import com.example.demo.cash.service.CashSettingsService;
import com.example.demo.common.enums.CashMovementType;
import com.example.demo.common.enums.CashStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.common.enums.UserRole;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CashServiceEventPaymentTest {

    @Mock private CashRegisterRepository cashRegisterRepository;
    @Mock private CashMovementRepository cashMovementRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private EventPaymentRepository eventPaymentRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private CashSettingsService cashSettingsService;

    @InjectMocks private CashService cashService;

    private CashRegister cash;
    private User user;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setId(10L);
        Branch branch = new Branch();
        branch.setId(20L);
        branch.setTenant(tenant);
        branch.setName("Sucursal A");
        user = new User();
        user.setId(30L);
        user.setName("Cajero");

        cash = new CashRegister();
        cash.setId(45L);
        cash.setPublicId("cash-45");
        cash.setTenant(tenant);
        cash.setBranch(branch);
        cash.setOpenedBy(user);
        cash.setOpeningAmount(bd("500"));
        cash.setOpenedAt(LocalDateTime.of(2026, 8, 14, 8, 0));
        cash.setStatus(CashStatus.OPEN);

        TenantContext.set(new TenantContext.TenantInfo(10L, 20L, 30L, UserRole.ADMIN));
        lenient().when(cashRegisterRepository.findByBranch_IdAndStatus(20L, CashStatus.OPEN))
                .thenReturn(Optional.of(cash));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void cashCombinesPosAndEventExactlyOnce() {
        stubSales("1000", "0", "0", "1300", "0", "0", "0", "0");

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("1000", response.getPosCashSales());
        assertAmount("1300", response.getEventCashPayments());
        assertAmount("2300", response.getCashSales());
        assertAmount("2800", response.getExpectedCash());
    }

    @Test
    void cardCombinesSourcesWithoutIncreasingPhysicalCash() {
        stubSales("0", "2000", "0", "0", "1500", "0", "0", "0");

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("3500", response.getCardSales());
        assertAmount("500", response.getExpectedCash());
    }

    @Test
    void transferCombinesSourcesWithoutIncreasingPhysicalCash() {
        stubSales("0", "0", "500", "0", "0", "2500", "0", "0");

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("3000", response.getTransferSales());
        assertAmount("500", response.getExpectedCash());
    }

    @Test
    void mixedSummaryUsesConsolidatedFormula() {
        cash.setOpeningAmount(bd("1000"));
        stubSales("2000", "3000", "1000", "1500", "500", "2000", "200", "400");

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("3500", response.getCashSales());
        assertAmount("3500", response.getCardSales());
        assertAmount("3000", response.getTransferSales());
        assertAmount("4300", response.getExpectedCash());
    }

    @Test
    void closeCashKeepsSameConsolidatedTotals() {
        cash.setOpeningAmount(bd("1000"));
        when(cashRegisterRepository.findByBranch_IdAndStatusForUpdate(20L, CashStatus.OPEN))
                .thenReturn(Optional.of(cash));
        when(userRepository.findById(30L)).thenReturn(Optional.of(user));
        stubSales("2000", "3000", "1000", "1500", "500", "2000", "200", "400");
        CloseCashRequest request = new CloseCashRequest();
        request.setCountedCash(bd("4300"));

        CashRegisterResponse response = cashService.closeCash(request);

        assertAmount("4300", response.getExpectedCash());
        assertAmount("3500", response.getCashSales());
        assertEquals(CashStatus.CLOSED, cash.getStatus());
        verify(cashRegisterRepository).save(cash);
    }

    @Test
    void withdrawalAvailabilityIncludesEventCash() {
        when(cashRegisterRepository.findByBranch_IdAndStatusForUpdate(20L, CashStatus.OPEN))
                .thenReturn(Optional.of(cash));
        when(userRepository.findById(30L)).thenReturn(Optional.of(user));
        stubSales("0", "0", "0", "1300", "0", "0", "0", "0");
        when(cashMovementRepository.save(any(CashMovement.class))).thenAnswer(invocation -> {
            CashMovement movement = invocation.getArgument(0);
            movement.setPublicId("movement-1");
            movement.setCreatedAt(LocalDateTime.now());
            return movement;
        });
        CashMovementRequest request = new CashMovementRequest();
        request.setAmount(bd("1500"));
        request.setReason("Retiro operativo");

        cashService.withdraw(request);

        verify(cashMovementRepository).save(any(CashMovement.class));
    }

    @Test
    void eventQueriesAreScopedToTheExplicitCashRegister() {
        stubSales("0", "0", "0", "0", "0", "0", "0", "0");

        cashService.currentCash();

        verify(eventPaymentRepository).sumByCashRegisterAndPaymentMethod(45L, PaymentMethod.CASH);
        verify(eventPaymentRepository).sumByCashRegisterAndPaymentMethod(45L, PaymentMethod.CARD);
        verify(eventPaymentRepository).sumByCashRegisterAndPaymentMethod(45L, PaymentMethod.TRANSFER);
    }

    private void stubSales(String posCash, String posCard, String posTransfer,
                           String eventCash, String eventCard, String eventTransfer,
                           String deposits, String withdrawals) {
        when(paymentRepository.sumCashPayments(any(), any(), any())).thenReturn(bd(posCash));
        when(paymentRepository.sumCardPayments(any(), any(), any())).thenReturn(bd(posCard));
        when(paymentRepository.sumTransferPayments(any(), any(), any())).thenReturn(bd(posTransfer));
        when(eventPaymentRepository.sumByCashRegisterAndPaymentMethod(45L, PaymentMethod.CASH))
                .thenReturn(bd(eventCash));
        when(eventPaymentRepository.sumByCashRegisterAndPaymentMethod(45L, PaymentMethod.CARD))
                .thenReturn(bd(eventCard));
        when(eventPaymentRepository.sumByCashRegisterAndPaymentMethod(45L, PaymentMethod.TRANSFER))
                .thenReturn(bd(eventTransfer));
        when(cashMovementRepository.sumByCashRegisterAndType(45L, CashMovementType.DEPOSIT))
                .thenReturn(bd(deposits));
        when(cashMovementRepository.sumByCashRegisterAndType(45L, CashMovementType.WITHDRAWAL))
                .thenReturn(bd(withdrawals));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual));
    }
}
