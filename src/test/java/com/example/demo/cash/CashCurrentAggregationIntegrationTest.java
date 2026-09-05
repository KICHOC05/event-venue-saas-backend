package com.example.demo.cash;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.cash.dto.CashRegisterResponse;
import com.example.demo.cash.model.CashMovement;
import com.example.demo.cash.model.CashRegister;
import com.example.demo.cash.repository.CashMovementRepository;
import com.example.demo.cash.repository.CashRegisterRepository;
import com.example.demo.cash.service.CashService;
import com.example.demo.common.enums.CashMovementType;
import com.example.demo.common.enums.CashStatus;
import com.example.demo.common.enums.EventStatus;
import com.example.demo.common.enums.OrderStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.common.enums.ProductType;
import com.example.demo.common.enums.UserRole;
import com.example.demo.event.model.EventBooking;
import com.example.demo.event.model.EventPayment;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.order.model.Order;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.payment.model.Payment;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.product.model.Product;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cash-current-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("test")
class CashCurrentAggregationIntegrationTest {

    @Autowired private CashService cashService;
    @Autowired private CashRegisterRepository cashRegisterRepository;
    @Autowired private CashMovementRepository cashMovementRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private EventPaymentRepository eventPaymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private EventBookingRepository eventBookingRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Tenant tenant;
    private Branch branch;
    private User user;
    private CashRegister cashRegister;
    private LocalDateTime openedAt;
    private long eventNumber;

    @BeforeEach
    void setUp() {
        eventPaymentRepository.deleteAll();
        cashMovementRepository.deleteAll();
        paymentRepository.deleteAll();
        eventBookingRepository.deleteAll();
        orderRepository.deleteAll();
        cashRegisterRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        branchRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant = createTenant("Tenant principal");
        branch = createBranch(tenant, "Sucursal principal");
        user = createUser(tenant, branch, "principal@example.test");
        openedAt = LocalDateTime.now().minusHours(2);
        cashRegister = createCashRegister(tenant, branch, user, "500.00", openedAt);
        eventNumber = 1L;

        TenantContext.set(new TenantContext.TenantInfo(
                tenant.getId(), branch.getId(), user.getId(), UserRole.ADMIN));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void newlyOpenedCashHasZeroTotalsAndOpeningAsExpectedCash() {
        CashRegisterResponse response = cashService.currentCash();

        assertAmount("0.00", response.getCashSales());
        assertAmount("0.00", response.getCardSales());
        assertAmount("0.00", response.getTransferSales());
        assertAmount("0.00", response.getDepositTotal());
        assertAmount("0.00", response.getWithdrawalTotal());
        assertAmount("500.00", response.getExpectedCash());
    }

    @Test
    void cashPaymentsFromPosAndEventsAreCombinedExactlyOnce() {
        createPosPayment(tenant, branch, user, "100.10", PaymentMethod.CASH, OrderStatus.CLOSED);
        createEventPayment(tenant, branch, cashRegister, "50.20", PaymentMethod.CASH,
                EventStatus.CONFIRMED);

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("100.10", response.getPosCashSales());
        assertAmount("50.20", response.getEventCashPayments());
        assertAmount("150.30", response.getCashSales());
        assertAmount("650.30", response.getExpectedCash());
    }

    @Test
    void cardPaymentsDoNotIncreasePhysicalCash() {
        createPosPayment(tenant, branch, user, "100.00", PaymentMethod.CARD, OrderStatus.CLOSED);
        createEventPayment(tenant, branch, cashRegister, "25.00", PaymentMethod.CARD,
                EventStatus.CONFIRMED);

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("125.00", response.getCardSales());
        assertAmount("500.00", response.getExpectedCash());
    }

    @Test
    void transferPaymentsDoNotIncreasePhysicalCash() {
        createPosPayment(tenant, branch, user, "80.00", PaymentMethod.TRANSFER, OrderStatus.CLOSED);
        createEventPayment(tenant, branch, cashRegister, "20.00", PaymentMethod.TRANSFER,
                EventStatus.CONFIRMED);

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("100.00", response.getTransferSales());
        assertAmount("500.00", response.getExpectedCash());
    }

    @Test
    void mixedMethodsPreserveSalesAndExpectedAmountContract() {
        createPosPayment(tenant, branch, user, "100.00", PaymentMethod.CASH, OrderStatus.CLOSED);
        createPosPayment(tenant, branch, user, "200.00", PaymentMethod.CARD, OrderStatus.CLOSED);
        createEventPayment(tenant, branch, cashRegister, "300.00", PaymentMethod.TRANSFER,
                EventStatus.CONFIRMED);

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("100.00", response.getCashSales());
        assertAmount("200.00", response.getCardSales());
        assertAmount("300.00", response.getTransferSales());
        assertAmount("600.00", response.getSalesTotal());
        assertAmount("600.00", response.getExpectedCash());
        assertAmount("1100.00", response.getExpectedAmount());
    }

    @Test
    void depositsIncreaseExpectedCash() {
        createMovement(tenant, branch, cashRegister, user,
                CashMovementType.DEPOSIT, "75.00", false);

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("75.00", response.getDepositTotal());
        assertAmount("575.00", response.getExpectedCash());
    }

    @Test
    void withdrawalsDecreaseExpectedCash() {
        createMovement(tenant, branch, cashRegister, user,
                CashMovementType.WITHDRAWAL, "40.00", false);

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("40.00", response.getWithdrawalTotal());
        assertAmount("460.00", response.getExpectedCash());
    }

    @Test
    void voidedMovementsAreExcludedForBothTypes() {
        createMovement(tenant, branch, cashRegister, user,
                CashMovementType.DEPOSIT, "100.00", true);
        createMovement(tenant, branch, cashRegister, user,
                CashMovementType.WITHDRAWAL, "90.00", true);

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("0.00", response.getDepositTotal());
        assertAmount("0.00", response.getWithdrawalTotal());
        assertAmount("500.00", response.getExpectedCash());
    }

    @Test
    void paymentsAndMovementsFromAnotherTenantAreExcluded() {
        Tenant otherTenant = createTenant("Otro tenant");
        Branch otherBranch = createBranch(otherTenant, "Sucursal externa");
        User otherUser = createUser(otherTenant, otherBranch, "other-tenant@example.test");
        CashRegister otherCash = createCashRegister(
                otherTenant, otherBranch, otherUser, "900.00", openedAt);
        createPosPayment(otherTenant, otherBranch, otherUser,
                "1000.00", PaymentMethod.CASH, OrderStatus.CLOSED);
        createEventPayment(otherTenant, otherBranch, otherCash,
                "1000.00", PaymentMethod.CASH, EventStatus.CONFIRMED);
        createMovement(otherTenant, otherBranch, otherCash, otherUser,
                CashMovementType.DEPOSIT, "1000.00", false);

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("0.00", response.getSalesTotal());
        assertAmount("0.00", response.getDepositTotal());
        assertAmount("500.00", response.getExpectedCash());
    }

    @Test
    void paymentsAndMovementsFromAnotherBranchAreExcluded() {
        Branch otherBranch = createBranch(tenant, "Otra sucursal");
        User otherUser = createUser(tenant, otherBranch, "other-branch@example.test");
        CashRegister otherCash = createCashRegister(
                tenant, otherBranch, otherUser, "200.00", openedAt);
        createPosPayment(tenant, otherBranch, otherUser,
                "700.00", PaymentMethod.CASH, OrderStatus.CLOSED);
        createEventPayment(tenant, otherBranch, otherCash,
                "800.00", PaymentMethod.CASH, EventStatus.CONFIRMED);
        createMovement(tenant, otherBranch, otherCash, otherUser,
                CashMovementType.WITHDRAWAL, "100.00", false);

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("0.00", response.getSalesTotal());
        assertAmount("0.00", response.getWithdrawalTotal());
        assertAmount("500.00", response.getExpectedCash());
    }

    @Test
    void persistedPaymentsRemainCountedAfterOrderOrEventCancellation() {
        createPosPayment(tenant, branch, user,
                "100.00", PaymentMethod.CASH, OrderStatus.CANCELLED);
        createEventPayment(tenant, branch, cashRegister,
                "50.00", PaymentMethod.CASH, EventStatus.CANCELLED);

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("150.00", response.getCashSales());
        assertAmount("650.00", response.getExpectedCash());
    }

    @Test
    void decimalScaleAndExpectedCashPrecisionArePreserved() {
        createPosPayment(tenant, branch, user,
                "10.11", PaymentMethod.CASH, OrderStatus.CLOSED);
        createEventPayment(tenant, branch, cashRegister,
                "20.22", PaymentMethod.CASH, EventStatus.CONFIRMED);
        createMovement(tenant, branch, cashRegister, user,
                CashMovementType.DEPOSIT, "0.03", false);
        createMovement(tenant, branch, cashRegister, user,
                CashMovementType.WITHDRAWAL, "0.01", false);

        CashRegisterResponse response = cashService.currentCash();

        assertAmount("30.33", response.getCashSales());
        assertAmount("530.35", response.getExpectedCash());
        assertEquals(2, response.getCashSales().scale());
        assertEquals(2, response.getExpectedCash().scale());
    }

    @Test
    void currentCashExecutesCashLookupAndTwoAggregateQueries() {
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        cashService.currentCash();

        assertEquals(3L, statistics.getPrepareStatementCount());
    }

    private Tenant createTenant(String businessName) {
        Tenant value = new Tenant();
        value.setBusinessName(businessName);
        return tenantRepository.saveAndFlush(value);
    }

    private Branch createBranch(Tenant owner, String name) {
        Branch value = new Branch();
        value.setTenant(owner);
        value.setName(name);
        return branchRepository.saveAndFlush(value);
    }

    private User createUser(Tenant owner, Branch ownerBranch, String email) {
        User value = new User();
        value.setTenant(owner);
        value.setBranch(ownerBranch);
        value.setName("Usuario de prueba");
        value.setEmail(email);
        value.setPassword("not-a-real-password");
        value.setRole(UserRole.ADMIN);
        return userRepository.saveAndFlush(value);
    }

    private CashRegister createCashRegister(
            Tenant owner,
            Branch ownerBranch,
            User openedBy,
            String openingAmount,
            LocalDateTime openingTime) {
        CashRegister value = new CashRegister();
        value.setTenant(owner);
        value.setBranch(ownerBranch);
        value.setOpenedBy(openedBy);
        value.setOpeningAmount(bd(openingAmount));
        value.setOpenedAt(openingTime);
        value.setStatus(CashStatus.OPEN);
        return cashRegisterRepository.saveAndFlush(value);
    }

    private void createPosPayment(
            Tenant owner,
            Branch ownerBranch,
            User receivedBy,
            String amount,
            PaymentMethod method,
            OrderStatus orderStatus) {
        Order order = new Order();
        order.setTenant(owner);
        order.setBranch(ownerBranch);
        order.setUser(receivedBy);
        order.setStatus(orderStatus);
        order.setTotalAmount(bd(amount));
        order.setSubtotal(bd(amount));
        order.setTax(BigDecimal.ZERO);
        order.setCreatedAt(openedAt.plusMinutes(10));
        order = orderRepository.saveAndFlush(order);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setTenant(owner);
        payment.setBranch(ownerBranch);
        payment.setUser(receivedBy);
        payment.setAmount(bd(amount));
        payment.setPaymentMethod(method);
        payment.setCreatedAt(openedAt.plusMinutes(15));
        paymentRepository.saveAndFlush(payment);
    }

    private void createEventPayment(
            Tenant owner,
            Branch ownerBranch,
            CashRegister paymentCash,
            String amount,
            PaymentMethod method,
            EventStatus eventStatus) {
        Product product = new Product();
        product.setTenant(owner);
        product.setName("Paquete " + eventNumber);
        product.setPrice(bd("1000.00"));
        product.setType(ProductType.PACKAGE);
        product.setActive(true);
        product.setDepartment("Eventos");
        product = productRepository.saveAndFlush(product);

        EventBooking event = EventBooking.builder()
                .tenant(owner)
                .branch(ownerBranch)
                .eventNumber(eventNumber++)
                .packageProduct(product)
                .customerName("Cliente")
                .childName("Niño")
                .eventDate(LocalDate.now().plusDays(10))
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(19, 0))
                .eventPrice(bd("1000.00"))
                .depositAmount(bd(amount))
                .remainingAmount(BigDecimal.ZERO)
                .status(eventStatus)
                .build();
        event = eventBookingRepository.saveAndFlush(event);

        EventPayment payment = EventPayment.builder()
                .eventBooking(event)
                .tenant(owner)
                .branch(ownerBranch)
                .cashRegister(paymentCash)
                .amount(bd(amount))
                .eventPriceAtPayment(event.getEventPrice())
                .paymentMethod(method)
                .build();
        eventPaymentRepository.saveAndFlush(payment);
    }

    private void createMovement(
            Tenant owner,
            Branch ownerBranch,
            CashRegister movementCash,
            User createdBy,
            CashMovementType type,
            String amount,
            boolean voided) {
        CashMovement movement = new CashMovement();
        movement.setTenant(owner);
        movement.setBranch(ownerBranch);
        movement.setCashRegister(movementCash);
        movement.setUser(createdBy);
        movement.setType(type);
        movement.setAmount(bd(amount));
        movement.setReason("Prueba");
        movement.setVoided(voided);
        cashMovementRepository.saveAndFlush(movement);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual));
    }
}
