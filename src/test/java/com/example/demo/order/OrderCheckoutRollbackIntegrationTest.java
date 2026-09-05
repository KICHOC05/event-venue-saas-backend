package com.example.demo.order;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.cash.model.CashRegister;
import com.example.demo.cash.repository.CashRegisterRepository;
import com.example.demo.common.enums.CashStatus;
import com.example.demo.common.enums.OrderStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.common.enums.UserRole;
import com.example.demo.loyalty.service.LoyaltyService;
import com.example.demo.order.dto.CheckoutRequest;
import com.example.demo.order.model.Order;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.service.OrderService;
import com.example.demo.payment.dto.PaymentRequest;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:order-checkout-rollback-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class OrderCheckoutRollbackIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CashRegisterRepository cashRegisterRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private LoyaltyService loyaltyService;

    private Tenant tenant;
    private Branch branch;
    private User user;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setBusinessName("Tenant rollback " + UUID.randomUUID());
        tenant = tenantRepository.save(tenant);

        branch = new Branch();
        branch.setTenant(tenant);
        branch.setName("Sucursal rollback " + UUID.randomUUID());
        branch = branchRepository.save(branch);

        user = new User();
        user.setTenant(tenant);
        user.setBranch(branch);
        user.setName("Cajero rollback");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPassword("test-password");
        user.setRole(UserRole.ADMIN);
        user = userRepository.save(user);

        CashRegister cashRegister = new CashRegister();
        cashRegister.setTenant(tenant);
        cashRegister.setBranch(branch);
        cashRegister.setOpenedBy(user);
        cashRegister.setOpeningAmount(BigDecimal.ZERO);
        cashRegister.setOpenedAt(LocalDateTime.now());
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
    void failureDuringCloseSideEffectsRollsBackPaymentAndOrderClose() {
        Order order = createOrder();
        doThrow(new IllegalStateException("simulated loyalty failure"))
                .when(loyaltyService).registerVisits(any(), anyList());

        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(order.getPublicId(), checkout()));

        assertEquals(0, paymentRepository.findAllByOrder_Id(order.getId()).size());
        Order persisted = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.OPEN, persisted.getStatus());
        assertEquals(null, persisted.getClosedAt());
    }

    private Order createOrder() {
        Order order = new Order();
        order.setTenant(tenant);
        order.setBranch(branch);
        order.setUser(user);
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(new BigDecimal("100.00"));
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setTax(BigDecimal.ZERO);
        return orderRepository.save(order);
    }

    private CheckoutRequest checkout() {
        PaymentRequest payment = new PaymentRequest();
        payment.setAmount(new BigDecimal("100.00"));
        payment.setPaymentMethod(PaymentMethod.CASH);

        CheckoutRequest request = new CheckoutRequest();
        request.setRequestId("rollback-checkout");
        request.setPayment(payment);
        return request;
    }
}
