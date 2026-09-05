package com.example.demo.order;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.cash.model.CashRegister;
import com.example.demo.cash.repository.CashRegisterRepository;
import com.example.demo.client.model.Client;
import com.example.demo.client.repository.ClientRepository;
import com.example.demo.common.enums.CashStatus;
import com.example.demo.common.enums.OrderItemStatus;
import com.example.demo.common.enums.OrderStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.common.enums.ProductType;
import com.example.demo.common.enums.UserRole;
import com.example.demo.loyalty.model.LoyaltyProgram;
import com.example.demo.loyalty.repository.ClientLoyaltyVisitRepository;
import com.example.demo.loyalty.repository.LoyaltyProgramRepository;
import com.example.demo.order.dto.CheckoutRequest;
import com.example.demo.order.dto.CheckoutResponse;
import com.example.demo.order.model.Order;
import com.example.demo.order.model.OrderItem;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.service.OrderService;
import com.example.demo.payment.dto.PaymentRequest;
import com.example.demo.payment.dto.PaymentResponse;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.payment.service.PaymentService;
import com.example.demo.product.model.Product;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:order-checkout-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class OrderCheckoutIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CashRegisterRepository cashRegisterRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LoyaltyProgramRepository loyaltyProgramRepository;
    @Autowired private ClientLoyaltyVisitRepository loyaltyVisitRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private TenantRepository tenantRepository;

    private Tenant tenant;
    private Branch branch;
    private User user;
    private CashRegister cashRegister;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setBusinessName("Tenant checkout " + UUID.randomUUID());
        tenant = tenantRepository.save(tenant);

        branch = new Branch();
        branch.setTenant(tenant);
        branch.setName("Sucursal checkout " + UUID.randomUUID());
        branch = branchRepository.save(branch);

        user = new User();
        user.setTenant(tenant);
        user.setBranch(branch);
        user.setName("Cajero checkout");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPassword("test-password");
        user.setRole(UserRole.ADMIN);
        user = userRepository.save(user);

        cashRegister = new CashRegister();
        cashRegister.setTenant(tenant);
        cashRegister.setBranch(branch);
        cashRegister.setOpenedBy(user);
        cashRegister.setOpeningAmount(BigDecimal.ZERO);
        cashRegister.setOpenedAt(LocalDateTime.now());
        cashRegister.setStatus(CashStatus.OPEN);
        cashRegister = cashRegisterRepository.save(cashRegister);

        setContext(tenant.getId(), branch.getId(), user.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void partialPaymentKeepsOrderOpenWithRemainingBalance() {
        Order order = createOrder("100.00", OrderStatus.OPEN, null);

        CheckoutResponse result = orderService.checkout(
                order.getPublicId(), checkout("partial-1", "40.00", PaymentMethod.CASH));

        assertFalse(result.isClosed());
        assertEquals(OrderStatus.PARTIALLY_PAID, result.getOrder().getStatus());
        assertMoney("60.00", result.getPayment().getRemainingAmount());
        assertMoney("40.00", paymentRepository.sumPaymentsByOrderId(order.getId()));
    }

    @Test
    void exactCashPaymentClosesOrderAtomically() {
        assertExactPaymentCloses(PaymentMethod.CASH);
    }

    @Test
    void exactCardPaymentClosesOrderAtomically() {
        assertExactPaymentCloses(PaymentMethod.CARD);
    }

    @Test
    void exactTransferPaymentClosesOrderAtomically() {
        assertExactPaymentCloses(PaymentMethod.TRANSFER);
    }

    @Test
    void cashOverpaymentAppliesOnlyBalanceAndReturnsChange() {
        Order order = createOrder("100.00", OrderStatus.OPEN, null);

        CheckoutResponse result = orderService.checkout(
                order.getPublicId(), checkout("cash-change", "120.00", PaymentMethod.CASH));

        assertTrue(result.isClosed());
        assertMoney("100.00", result.getPayment().getAmountApplied());
        assertMoney("120.00", result.getPayment().getAmountReceived());
        assertMoney("20.00", result.getPayment().getChange());
        assertMoney("100.00", paymentRepository.sumPaymentsByOrderId(order.getId()));
    }

    @Test
    void cardOverpaymentIsRejectedWithoutPersistingPayment() {
        assertOverpaymentRejected(PaymentMethod.CARD);
    }

    @Test
    void transferOverpaymentIsRejectedWithoutPersistingPayment() {
        assertOverpaymentRejected(PaymentMethod.TRANSFER);
    }

    @Test
    void existingPartialPaymentCanBeCompletedByCheckout() {
        Order order = createOrder("100.00", OrderStatus.OPEN, null);
        paymentService.registerPayment(order.getPublicId(), payment("40.00", PaymentMethod.CARD));

        CheckoutResponse result = orderService.checkout(
                order.getPublicId(), checkout("mixed-payment", "60.00", PaymentMethod.CASH));

        assertTrue(result.isClosed());
        assertEquals(2, result.getOrder().getPayments().size());
        assertMoney("100.00", result.getPayment().getTotalPaid());
    }

    @Test
    void newCheckoutForClosedOrderIsRejected() {
        Order order = createOrder("100.00", OrderStatus.CLOSED, null);

        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(order.getPublicId(), checkout("closed-new", "100.00", PaymentMethod.CASH)));
        assertEquals(0, paymentRepository.findAllByOrder_Id(order.getId()).size());
    }

    @Test
    void checkoutForCancelledOrderIsRejected() {
        Order order = createOrder("100.00", OrderStatus.CANCELLED, null);

        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(order.getPublicId(), checkout("cancelled", "100.00", PaymentMethod.CASH)));
        assertEquals(0, paymentRepository.findAllByOrder_Id(order.getId()).size());
    }

    @Test
    void checkoutCannotReadOrderFromAnotherTenant() {
        Order order = createOrder("100.00", OrderStatus.OPEN, null);
        Tenant otherTenant = tenantRepository.save(newTenant());
        setContext(otherTenant.getId(), branch.getId(), user.getId());

        assertThrows(EntityNotFoundException.class,
                () -> orderService.checkout(order.getPublicId(), checkout("foreign-tenant", "100.00", PaymentMethod.CASH)));
    }

    @Test
    void checkoutCannotReadOrderFromAnotherBranch() {
        Order order = createOrder("100.00", OrderStatus.OPEN, null);
        Branch otherBranch = new Branch();
        otherBranch.setTenant(tenant);
        otherBranch.setName("Otra sucursal " + UUID.randomUUID());
        otherBranch = branchRepository.save(otherBranch);
        setContext(tenant.getId(), otherBranch.getId(), user.getId());

        assertThrows(EntityNotFoundException.class,
                () -> orderService.checkout(order.getPublicId(), checkout("foreign-branch", "100.00", PaymentMethod.CASH)));
    }

    @Test
    void closedCashRegisterRejectsCheckoutWithoutFinancialChanges() {
        closeCashRegister();
        Order order = createOrder("100.00", OrderStatus.OPEN, null);

        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(order.getPublicId(), checkout("cash-closed", "100.00", PaymentMethod.CASH)));

        assertEquals(0, paymentRepository.findAllByOrder_Id(order.getId()).size());
        assertEquals(OrderStatus.OPEN, orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void invalidPaymentAmountRollsBackCheckout() {
        Order order = createOrder("100.00", OrderStatus.OPEN, null);

        assertThrows(IllegalArgumentException.class,
                () -> orderService.checkout(order.getPublicId(), checkout("invalid-zero", "0.00", PaymentMethod.CASH)));

        assertEquals(0, paymentRepository.findAllByOrder_Id(order.getId()).size());
        assertEquals(OrderStatus.OPEN, orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void sameRequestIdIsIdempotentAndReturnsOriginalResult() {
        Order order = createOrder("100.00", OrderStatus.OPEN, null);
        CheckoutRequest request = checkout("retry-safe", "100.00", PaymentMethod.CASH);

        CheckoutResponse first = orderService.checkout(order.getPublicId(), request);
        closeCashRegister();
        CheckoutResponse retry = orderService.checkout(order.getPublicId(), request);

        assertTrue(first.isClosed());
        assertTrue(retry.isClosed());
        assertEquals(first.getOrder().getPayments().getFirst().getPublicId(),
                retry.getOrder().getPayments().getFirst().getPublicId());
        assertEquals(1, paymentRepository.findAllByOrder_Id(order.getId()).size());
    }

    @Test
    void sameRequestIdCannotBeReusedForDifferentPaymentData() {
        Order order = createOrder("100.00", OrderStatus.OPEN, null);
        orderService.checkout(order.getPublicId(), checkout("bound-request", "40.00", PaymentMethod.CASH));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.checkout(
                        order.getPublicId(), checkout("bound-request", "30.00", PaymentMethod.CASH)));

        assertEquals(1, paymentRepository.findAllByOrder_Id(order.getId()).size());
        assertMoney("40.00", paymentRepository.sumPaymentsByOrderId(order.getId()));
    }

    @Test
    void finalResponseContainsUpdatedOrderPaymentAndChange() {
        Order order = createOrder("75.00", OrderStatus.OPEN, null);

        CheckoutResponse result = orderService.checkout(
                order.getPublicId(), checkout("response", "100.00", PaymentMethod.CASH));

        assertNotNull(result.getOrder());
        assertNotNull(result.getPayment());
        assertEquals(OrderStatus.CLOSED, result.getOrder().getStatus());
        assertEquals(1, result.getOrder().getPayments().size());
        assertMoney("25.00", result.getPayment().getChange());
        assertNotNull(result.getOrder().getClosedAt());
    }

    @Test
    void eligibleFrequentClientReceivesOneLoyaltyVisit() {
        Product product = createProduct();
        Client client = createFrequentClient();
        LoyaltyProgram program = new LoyaltyProgram();
        program.setTenant(tenant);
        program.setBranch(branch);
        program.setName("Programa checkout");
        program.setQualifyingProduct(product);
        program.setRequiredPurchases(5);
        program.setRewardQuantity(1);
        program.setActive(true);
        loyaltyProgramRepository.save(program);

        Order order = createOrder("100.00", OrderStatus.OPEN, client);
        createItem(order, product, "100.00");

        CheckoutResponse result = orderService.checkout(
                order.getPublicId(), checkout("loyalty", "100.00", PaymentMethod.CASH));

        assertTrue(result.isClosed());
        assertEquals(1, loyaltyVisitRepository
                .findByClient_IdAndLoyaltyProgram_Id(client.getId(), program.getId()).size());
    }

    @Test
    void simultaneousRetriesWithSameRequestIdCreateOnePayment() throws Exception {
        Order order = createOrder("100.00", OrderStatus.OPEN, null);
        CheckoutRequest request = checkout("concurrent-retry", "100.00", PaymentMethod.CASH);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CheckoutResponse> first = executor.submit(() -> concurrentCheckout(order, request, ready, start));
            Future<CheckoutResponse> second = executor.submit(() -> concurrentCheckout(order, request, ready, start));
            ready.await();
            start.countDown();

            assertTrue(first.get().isClosed());
            assertTrue(second.get().isClosed());
        }

        assertEquals(1, paymentRepository.findAllByOrder_Id(order.getId()).size());
    }

    @Test
    void legacyPaymentAndCloseEndpointsServicesRemainCompatible() {
        Order order = createOrder("100.00", OrderStatus.OPEN, null);

        PaymentResponse payment = paymentService.registerPayment(
                order.getPublicId(), payment("100.00", PaymentMethod.TRANSFER));
        assertMoney("0.00", payment.getRemainingAmount());
        assertEquals(OrderStatus.OPEN, orderRepository.findById(order.getId()).orElseThrow().getStatus());

        assertEquals(OrderStatus.CLOSED, orderService.closeOrder(order.getPublicId()).getStatus());
    }

    private CheckoutResponse concurrentCheckout(
            Order order, CheckoutRequest request, CountDownLatch ready, CountDownLatch start) throws Exception {
        setContext(tenant.getId(), branch.getId(), user.getId());
        try {
            ready.countDown();
            start.await();
            return orderService.checkout(order.getPublicId(), request);
        } finally {
            TenantContext.clear();
        }
    }

    private void assertExactPaymentCloses(PaymentMethod method) {
        Order order = createOrder("100.00", OrderStatus.OPEN, null);
        CheckoutResponse result = orderService.checkout(
                order.getPublicId(), checkout("exact-" + method, "100.00", method));

        assertTrue(result.isClosed());
        assertEquals(OrderStatus.CLOSED, result.getOrder().getStatus());
        assertMoney("0.00", result.getPayment().getRemainingAmount());
        assertEquals(1, paymentRepository.findAllByOrder_Id(order.getId()).size());
    }

    private void assertOverpaymentRejected(PaymentMethod method) {
        Order order = createOrder("100.00", OrderStatus.OPEN, null);

        assertThrows(IllegalArgumentException.class,
                () -> orderService.checkout(order.getPublicId(), checkout("overpay-" + method, "120.00", method)));

        assertEquals(0, paymentRepository.findAllByOrder_Id(order.getId()).size());
        assertEquals(OrderStatus.OPEN, orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    private void setContext(Long tenantId, Long branchId, Long userId) {
        TenantContext.set(new TenantContext.TenantInfo(tenantId, branchId, userId, UserRole.ADMIN));
    }

    private Tenant newTenant() {
        Tenant value = new Tenant();
        value.setBusinessName("Tenant ajeno " + UUID.randomUUID());
        return value;
    }

    private Order createOrder(String total, OrderStatus status, Client client) {
        Order order = new Order();
        order.setTenant(tenant);
        order.setBranch(branch);
        order.setUser(user);
        order.setClient(client);
        order.setStatus(status);
        order.setSubtotal(new BigDecimal(total));
        order.setTotalAmount(new BigDecimal(total));
        order.setTax(BigDecimal.ZERO);
        if (status == OrderStatus.CLOSED) {
            order.setClosedAt(LocalDateTime.now());
        }
        return orderRepository.save(order);
    }

    private PaymentRequest payment(String amount, PaymentMethod method) {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal(amount));
        request.setPaymentMethod(method);
        if (method != PaymentMethod.CASH) {
            request.setReference("REF-" + UUID.randomUUID());
        }
        return request;
    }

    private CheckoutRequest checkout(String requestId, String amount, PaymentMethod method) {
        CheckoutRequest request = new CheckoutRequest();
        request.setRequestId(requestId);
        request.setPayment(payment(amount, method));
        return request;
    }

    private void closeCashRegister() {
        cashRegister.setStatus(CashStatus.CLOSED);
        cashRegister.setClosedAt(LocalDateTime.now());
        cashRegisterRepository.save(cashRegister);
    }

    private Product createProduct() {
        Product product = new Product();
        product.setTenant(tenant);
        product.setName("Producto lealtad " + UUID.randomUUID());
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(null);
        product.setType(ProductType.PRODUCT);
        product.setActive(true);
        product.setDepartment("POS");
        return productRepository.save(product);
    }

    private Client createFrequentClient() {
        Client client = new Client();
        client.setTenant(tenant);
        client.setBranch(branch);
        client.setParentName("Cliente frecuente");
        client.setFrequent(true);
        return clientRepository.save(client);
    }

    private void createItem(Order order, Product product, String subtotal) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal(subtotal));
        item.setSubtotal(new BigDecimal(subtotal));
        item.setStatus(OrderItemStatus.ACTIVE);
        item.setRewardItem(false);
        item.setIsEvent(false);
        orderItemRepository.save(item);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
