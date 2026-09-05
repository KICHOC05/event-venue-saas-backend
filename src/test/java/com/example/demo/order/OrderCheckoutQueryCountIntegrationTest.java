package com.example.demo.order;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.cash.model.CashRegister;
import com.example.demo.cash.repository.CashRegisterRepository;
import com.example.demo.common.enums.CashStatus;
import com.example.demo.common.enums.OrderStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.common.enums.UserRole;
import com.example.demo.order.model.Order;
import com.example.demo.order.dto.CheckoutRequest;
import com.example.demo.order.dto.CheckoutResponse;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.service.OrderService;
import com.example.demo.payment.dto.PaymentRequest;
import com.example.demo.payment.service.PaymentService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-checkout-query-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("test")
class OrderCheckoutQueryCountIntegrationTest {

    @Autowired private PaymentService paymentService;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private CashRegisterRepository cashRegisterRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Tenant tenant;
    private Branch branch;
    private User user;

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

        CashRegister cashRegister = new CashRegister();
        cashRegister.setTenant(tenant);
        cashRegister.setBranch(branch);
        cashRegister.setOpenedBy(user);
        cashRegister.setOpeningAmount(BigDecimal.ZERO);
        cashRegister.setOpenedAt(java.time.LocalDateTime.now());
        cashRegister.setStatus(CashStatus.OPEN);
        cashRegisterRepository.save(cashRegister);

        TenantContext.set(new TenantContext.TenantInfo(
                tenant.getId(), branch.getId(), user.getId(), UserRole.ADMIN));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void checkoutUsesFewerStatementsThanLegacyPaymentAndClose() {
        Order partialCash = createOrder("100.00");
        report("legacy-partial-cash", () -> {
            paymentService.registerPayment(partialCash.getPublicId(), payment("40.00", PaymentMethod.CASH));
        });
        Order exactCash = createOrder("100.00");
        QueryCounts legacy = measure("legacy-exact-cash-plus-close", () -> {
            paymentService.registerPayment(exactCash.getPublicId(), payment("100.00", PaymentMethod.CASH));
            orderService.closeOrder(exactCash.getPublicId());
        });
        Order cashChange = createOrder("100.00");
        report("legacy-cash-change-plus-close", () -> {
            paymentService.registerPayment(cashChange.getPublicId(), payment("120.00", PaymentMethod.CASH));
            orderService.closeOrder(cashChange.getPublicId());
        });
        Order exactCard = createOrder("100.00");
        report("legacy-card-plus-close", () -> {
            paymentService.registerPayment(exactCard.getPublicId(), payment("100.00", PaymentMethod.CARD));
            orderService.closeOrder(exactCard.getPublicId());
        });
        Order exactTransfer = createOrder("100.00");
        report("legacy-transfer-plus-close", () -> {
            paymentService.registerPayment(exactTransfer.getPublicId(), payment("100.00", PaymentMethod.TRANSFER));
            orderService.closeOrder(exactTransfer.getPublicId());
        });

        Order checkoutCash = createOrder("100.00");
        QueryCounts checkout = measure("checkout-exact-cash", () -> {
            CheckoutResponse response = orderService.checkout(
                    checkoutCash.getPublicId(), checkout("100.00", PaymentMethod.CASH));
            assertTrue(response.isClosed());
        });

        Order checkoutPartial = createOrder("100.00");
        report("checkout-partial-cash", () -> orderService.checkout(
                checkoutPartial.getPublicId(), checkout("40.00", PaymentMethod.CASH)));

        Order checkoutChange = createOrder("100.00");
        report("checkout-cash-change", () -> orderService.checkout(
                checkoutChange.getPublicId(), checkout("120.00", PaymentMethod.CASH)));

        Order checkoutCard = createOrder("100.00");
        report("checkout-card", () -> orderService.checkout(
                checkoutCard.getPublicId(), checkout("100.00", PaymentMethod.CARD)));

        Order checkoutTransfer = createOrder("100.00");
        report("checkout-transfer", () -> orderService.checkout(
                checkoutTransfer.getPublicId(), checkout("100.00", PaymentMethod.TRANSFER)));

        assertTrue(checkout.statements() < legacy.statements(),
                () -> "checkout=" + checkout.statements() + ", legacy=" + legacy.statements());
    }

    private void report(String label, Runnable operation) {
        measure(label, operation);
    }

    private QueryCounts measure(String label, Runnable operation) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        operation.run();
        QueryCounts counts = new QueryCounts(
                statistics.getPrepareStatementCount(),
                statistics.getEntityInsertCount(),
                statistics.getEntityUpdateCount());
        System.out.printf("CHECKOUT_QUERY_COUNT %s statements=%d approxSelects=%d inserts=%d updates=%d%n",
                label,
                counts.statements(),
                Math.max(0, counts.statements() - counts.inserts() - counts.updates()),
                counts.inserts(),
                counts.updates());
        return counts;
    }

    private Order createOrder(String total) {
        Order order = new Order();
        order.setTenant(tenant);
        order.setBranch(branch);
        order.setUser(user);
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(new BigDecimal(total));
        order.setTotalAmount(new BigDecimal(total));
        order.setTax(BigDecimal.ZERO);
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

    private CheckoutRequest checkout(String amount, PaymentMethod method) {
        CheckoutRequest request = new CheckoutRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setPayment(payment(amount, method));
        return request;
    }

    private record QueryCounts(long statements, long inserts, long updates) {
    }
}
