package com.example.demo.lazyloading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.common.enums.EventStatus;
import com.example.demo.common.enums.OrderItemStatus;
import com.example.demo.common.enums.OrderStatus;
import com.example.demo.common.enums.ProductType;
import com.example.demo.common.enums.UserRole;
import com.example.demo.dashboard.controller.DashboardController;
import com.example.demo.dashboard.dto.DashboardResponse;
import com.example.demo.event.model.EventBooking;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.order.controller.TimerController;
import com.example.demo.order.dto.ActiveSessionResponse;
import com.example.demo.order.dto.TimerHistoryResponse;
import com.example.demo.order.model.Order;
import com.example.demo.order.model.OrderItem;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.product.model.Product;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;

import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lazy-loading-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("test")
class DashboardAndTimerLazyLoadingIntegrationTest {

    @Autowired private DashboardController dashboardController;
    @Autowired private TimerController timerController;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private EventBookingRepository eventBookingRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboardSerializesUpcomingProductAfterFixturePersistenceContextIsClosed() throws Exception {
        DashboardFixture fixture = transactionTemplate.execute(status -> createDashboardFixture());
        TenantContext.set(new TenantContext.TenantInfo(
                fixture.tenantId(), fixture.branchId(), null, UserRole.ADMIN));

        Statistics statistics = statistics();
        statistics.clear();

        ResponseEntity<DashboardResponse> result = dashboardController.getDashboard();
        String json = objectMapper.writeValueAsString(result.getBody());

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getUpcomingEvents())
                .extracting(DashboardResponse.UpcomingEventDTO::getPackageName)
                .containsExactly(fixture.productName());
        assertThat(result.getBody().getInventory().getLowStockProducts())
                .anySatisfy(product -> {
                    assertThat(product.getPublicId()).isEqualTo(fixture.productPublicId());
                    assertThat(product.getName()).isEqualTo(fixture.productName());
                });
        assertThat(json).contains(fixture.productName(), fixture.productPublicId());
        assertThat(statistics.getEntityFetchCount()).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void timerEndpointsSerializeOrderAndProductDataInOneBoundedQuery() throws Exception {
        TimerFixture fixture = transactionTemplate.execute(status -> createTimerFixture());
        TenantContext.set(new TenantContext.TenantInfo(
                fixture.tenantId(), fixture.branchId(), fixture.userId(), UserRole.ADMIN));

        Statistics statistics = statistics();
        statistics.clear();

        List<ActiveSessionResponse> active = timerController.getActiveSessions();
        String activeJson = objectMapper.writeValueAsString(active);

        assertThat(active).hasSize(2);
        assertThat(active)
                .extracting(ActiveSessionResponse::getOrderPublicId)
                .containsOnly(fixture.orderPublicId());
        assertThat(active)
                .extracting(ActiveSessionResponse::getProductName)
                .containsOnly(fixture.productName());
        assertThat(activeJson).contains(fixture.orderPublicId(), fixture.productName());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1L);
        assertThat(statistics.getEntityFetchCount()).isZero();

        statistics.clear();
        Page<TimerHistoryResponse> history = timerController.getSessionHistory(
                null, null, null, 0, 10);
        String historyJson = objectMapper.writeValueAsString(history);

        assertThat(history.getContent()).hasSize(2);
        assertThat(history.getContent())
                .extracting(TimerHistoryResponse::getOrderPublicId)
                .containsOnly(fixture.orderPublicId());
        assertThat(historyJson).contains(fixture.orderPublicId(), fixture.productName());
        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 2L);
        assertThat(statistics.getEntityFetchCount()).isZero();
    }

    private DashboardFixture createDashboardFixture() {
        Tenant tenant = createTenant("Dashboard tenant");
        Branch branch = createBranch(tenant, "Dashboard branch");
        Product product = createProduct(tenant, "Paquete Estelar", ProductType.PACKAGE, 3);

        EventBooking event = EventBooking.builder()
                .tenant(tenant)
                .branch(branch)
                .eventNumber(1L)
                .packageProduct(product)
                .customerName("Familia Dashboard")
                .childName("Cliente infantil")
                .eventDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(17, 0))
                .guestChildren(12)
                .guestAdults(8)
                .eventPrice(new BigDecimal("500.00"))
                .depositAmount(new BigDecimal("100.00"))
                .remainingAmount(new BigDecimal("400.00"))
                .status(EventStatus.CONFIRMED)
                .build();
        eventBookingRepository.save(event);

        return new DashboardFixture(
                tenant.getId(), branch.getId(), product.getPublicId(), product.getName());
    }

    private TimerFixture createTimerFixture() {
        Tenant tenant = createTenant("Timer tenant");
        Branch branch = createBranch(tenant, "Timer branch");
        User user = createUser(tenant, branch);
        Product product = createProduct(tenant, "Juego por hora", ProductType.SERVICE, null);

        Order order = new Order();
        order.setTenant(tenant);
        order.setBranch(branch);
        order.setUser(user);
        order.setStatus(OrderStatus.OPEN);
        order.setCustomerName("Familia Timer");
        order = orderRepository.save(order);

        createTimerItem(order, product, "Niño uno", LocalDateTime.now().minusMinutes(5));
        createTimerItem(order, product, "Niño dos", LocalDateTime.now().minusMinutes(4));

        return new TimerFixture(
                tenant.getId(), branch.getId(), user.getId(), order.getPublicId(), product.getName());
    }

    private void createTimerItem(Order order, Product product, String childName, LocalDateTime start) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(product.getPrice());
        item.setSubtotal(product.getPrice());
        item.setChildName(childName);
        item.setStatus(OrderItemStatus.ACTIVE);
        item.setSessionStart(start);
        item.setSessionEnd(start.plusMinutes(60));
        item.setDurationMinutes(60);
        item.setActive(true);
        item.setIsEvent(false);
        item.setRewardItem(false);
        orderItemRepository.save(item);
    }

    private Tenant createTenant(String name) {
        Tenant tenant = new Tenant();
        tenant.setBusinessName(name + " " + UUID.randomUUID());
        return tenantRepository.save(tenant);
    }

    private Branch createBranch(Tenant tenant, String name) {
        Branch branch = new Branch();
        branch.setTenant(tenant);
        branch.setName(name + " " + UUID.randomUUID());
        return branchRepository.save(branch);
    }

    private User createUser(Tenant tenant, Branch branch) {
        User user = new User();
        user.setTenant(tenant);
        user.setBranch(branch);
        user.setName("Administrador timers");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPassword("test-password");
        user.setRole(UserRole.ADMIN);
        return userRepository.save(user);
    }

    private Product createProduct(Tenant tenant, String name, ProductType type, Integer stock) {
        Product product = new Product();
        product.setTenant(tenant);
        product.setName(name);
        product.setDescription("Fixture de lazy loading");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(stock);
        product.setType(type);
        product.setActive(true);
        product.setDepartment("Pruebas");
        product.setDurationMinutes(type == ProductType.SERVICE ? 60 : null);
        product.setRequiresSchedule(false);
        return productRepository.save(product);
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }

    private record DashboardFixture(
            Long tenantId,
            Long branchId,
            String productPublicId,
            String productName) {
    }

    private record TimerFixture(
            Long tenantId,
            Long branchId,
            Long userId,
            String orderPublicId,
            String productName) {
    }
}
