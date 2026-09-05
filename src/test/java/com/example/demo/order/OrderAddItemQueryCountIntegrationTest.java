package com.example.demo.order;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.common.enums.InventoryMode;
import com.example.demo.common.enums.OrderItemStatus;
import com.example.demo.common.enums.OrderStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.common.enums.ProductType;
import com.example.demo.common.enums.UserRole;
import com.example.demo.order.dto.OrderItemRequest;
import com.example.demo.order.dto.OrderResponse;
import com.example.demo.order.dto.UpdateOrderItemRequest;
import com.example.demo.order.model.Order;
import com.example.demo.order.model.OrderItem;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.service.OrderService;
import com.example.demo.payment.model.Payment;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.product.model.Product;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.settings.model.TaxSettings;
import com.example.demo.settings.model.TenantSettings;
import com.example.demo.settings.repository.TaxSettingsRepository;
import com.example.demo.settings.repository.TenantSettingsRepository;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityNotFoundException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-query-count-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("test")
class OrderAddItemQueryCountIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private TaxSettingsRepository taxSettingsRepository;
    @Autowired private TenantSettingsRepository tenantSettingsRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Tenant tenant;
    private Branch branch;
    private User user;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setBusinessName("Tenant " + UUID.randomUUID());
        tenant = tenantRepository.save(tenant);

        branch = new Branch();
        branch.setTenant(tenant);
        branch.setName("Sucursal " + UUID.randomUUID());
        branch = branchRepository.save(branch);

        user = new User();
        user.setTenant(tenant);
        user.setBranch(branch);
        user.setName("Cajero");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPassword("test-password");
        user.setRole(UserRole.ADMIN);
        user = userRepository.save(user);

        TenantSettings inventorySettings = new TenantSettings();
        inventorySettings.setTenant(tenant);
        inventorySettings.setInventoryMode(InventoryMode.WARNING);
        tenantSettingsRepository.save(inventorySettings);

        TaxSettings taxSettings = new TaxSettings();
        taxSettings.setTenant(tenant);
        taxSettings.setTaxEnabled(true);
        taxSettings.setTaxRate(new BigDecimal("0.1600"));
        taxSettingsRepository.save(taxSettings);

        TenantContext.set(new TenantContext.TenantInfo(
                tenant.getId(), branch.getId(), user.getId(), UserRole.ADMIN));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void firstItemWithInventoryUsesTenStatementsAndPreservesTotalsTaxStockAndProduct() {
        Order order = createOrder(OrderStatus.OPEN);
        Product product = createProduct("Primer producto", "10.00", 20);

        long queries = measure(() -> {
            OrderResponse response = orderService.addItem(order.getPublicId(), itemRequest(product, 2));
            assertEquals(1, response.getItems().size());
            assertAmount("20.00", response.getSubtotal());
            assertAmount("3.20", response.getTax());
            assertAmount("23.20", response.getTotalAmount());
            assertEquals(product.getPublicId(), response.getItems().getFirst().getProductPublicId());
            assertEquals(product.getName(), response.getItems().getFirst().getProductName());
        });

        assertEquals(10, queries);
        assertEquals(18, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void tenExistingItemsDoNotIncreaseTheAddItemQueryCount() {
        Order order = createOrder(OrderStatus.OPEN);
        for (int index = 0; index < 10; index++) {
            Product existingProduct = createProduct("Existente " + index, "2.00", 20);
            createOrderItem(order, existingProduct, 1);
        }
        Product newProduct = createProduct("Producto nuevo", "10.00", 20);

        long queries = measure(() -> {
            OrderResponse response = orderService.addItem(order.getPublicId(), itemRequest(newProduct, 1));
            assertEquals(11, response.getItems().size());
        });

        assertEquals(10, queries);
    }

    @Test
    void increasingExistingItemQuantityUsesNineStatementsAndAdjustsStock() {
        Order order = createOrder(OrderStatus.OPEN);
        Product product = createProduct("Producto actualizable", "10.00", 20);
        OrderItem item = createOrderItem(order, product, 1);
        UpdateOrderItemRequest request = new UpdateOrderItemRequest();
        request.setQuantity(3);

        long queries = measure(() -> {
            OrderResponse response = orderService.updateItemQuantity(
                    order.getPublicId(), item.getPublicId(), request);
            assertEquals(3, response.getItems().getFirst().getQuantity());
        });

        assertEquals(9, queries);
        assertEquals(18, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void firstItemWithoutInventoryUsesNineStatements() {
        Order order = createOrder(OrderStatus.OPEN);
        Product product = createProduct("Sin inventario", "10.00", null);

        long queries = measure(() -> {
            OrderResponse response = orderService.addItem(order.getPublicId(), itemRequest(product, 1));
            assertEquals(1, response.getItems().size());
        });

        assertEquals(9, queries);
    }

    @Test
    void decreasingExistingItemQuantityAdjustsTotalsAndRestoresStock() {
        Order order = createOrder(OrderStatus.OPEN);
        Product product = createProduct("Producto reducible", "10.00", 17);
        OrderItem item = createOrderItem(order, product, 3);
        UpdateOrderItemRequest request = new UpdateOrderItemRequest();
        request.setQuantity(1);

        OrderResponse response = orderService.updateItemQuantity(
                order.getPublicId(), item.getPublicId(), request);

        assertEquals(1, response.getItems().getFirst().getQuantity());
        assertAmount("10.00", response.getSubtotal());
        assertAmount("11.60", response.getTotalAmount());
        assertEquals(19, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void voidItemRestoresStockAndExcludesItFromTotals() {
        Order order = createOrder(OrderStatus.OPEN);
        Product product = createProduct("Producto anulable", "10.00", 18);
        OrderItem item = createOrderItem(order, product, 2);

        long queries = measure(() -> {
            OrderResponse response = orderService.voidItem(order.getPublicId(), item.getPublicId());
            assertEquals("VOIDED", response.getItems().getFirst().getStatus());
            assertAmount("0.00", response.getSubtotal());
            assertAmount("0.00", response.getTotalAmount());
        });

        assertEquals(8, queries);
        assertEquals(20, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void strictInventoryRejectsInsufficientStockWithoutPartialWrites() {
        TenantSettings settings = tenantSettingsRepository.findByTenant_Id(tenant.getId()).orElseThrow();
        settings.setInventoryMode(InventoryMode.STRICT);
        tenantSettingsRepository.save(settings);
        Order order = createOrder(OrderStatus.OPEN);
        Product product = createProduct("Stock insuficiente", "10.00", 1);

        assertThrows(IllegalStateException.class,
                () -> orderService.addItem(order.getPublicId(), itemRequest(product, 2)));

        assertEquals(0, orderItemRepository.findAllByOrder_Id(order.getId()).size());
        assertEquals(1, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void productFromAnotherTenantIsRejected() {
        Order order = createOrder(OrderStatus.OPEN);
        Tenant otherTenant = new Tenant();
        otherTenant.setBusinessName("Otro tenant " + UUID.randomUUID());
        otherTenant = tenantRepository.save(otherTenant);
        Product foreignProduct = createProduct(otherTenant, "Producto ajeno", "10.00", 10);

        assertThrows(EntityNotFoundException.class,
                () -> orderService.addItem(order.getPublicId(), itemRequest(foreignProduct, 1)));
        assertEquals(0, orderItemRepository.findAllByOrder_Id(order.getId()).size());
    }

    @Test
    void orderFromAnotherBranchIsRejected() {
        Order order = createOrder(OrderStatus.OPEN);
        Product product = createProduct("Producto local", "10.00", 10);
        Branch otherBranch = new Branch();
        otherBranch.setTenant(tenant);
        otherBranch.setName("Otra sucursal " + UUID.randomUUID());
        otherBranch = branchRepository.save(otherBranch);
        TenantContext.set(new TenantContext.TenantInfo(
                tenant.getId(), otherBranch.getId(), user.getId(), UserRole.ADMIN));

        assertThrows(EntityNotFoundException.class,
                () -> orderService.addItem(order.getPublicId(), itemRequest(product, 1)));
        assertEquals(0, orderItemRepository.findAllByOrder_Id(order.getId()).size());
    }

    @Test
    void closedOrderCannotBeModified() {
        Order order = createOrder(OrderStatus.CLOSED);
        Product product = createProduct("Producto cerrado", "10.00", 10);

        assertThrows(IllegalStateException.class,
                () -> orderService.addItem(order.getPublicId(), itemRequest(product, 1)));
        assertEquals(0, orderItemRepository.findAllByOrder_Id(order.getId()).size());
    }

    @Test
    void cancelledOrderCannotBeModified() {
        Order order = createOrder(OrderStatus.CANCELLED);
        Product product = createProduct("Producto cancelado", "10.00", 10);

        assertThrows(IllegalStateException.class,
                () -> orderService.addItem(order.getPublicId(), itemRequest(product, 1)));
        assertEquals(0, orderItemRepository.findAllByOrder_Id(order.getId()).size());
    }

    @Test
    void existingPaymentsRemainInTheAddItemResponse() {
        Order order = createOrder(OrderStatus.OPEN);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setTenant(tenant);
        payment.setBranch(branch);
        payment.setUser(user);
        payment.setAmount(new BigDecimal("5.00"));
        payment.setPaymentMethod(PaymentMethod.CARD);
        payment = paymentRepository.save(payment);
        Product product = createProduct("Producto con pago", "10.00", 10);

        OrderResponse response = orderService.addItem(order.getPublicId(), itemRequest(product, 1));

        assertEquals(1, response.getPayments().size());
        assertEquals(payment.getPublicId(), response.getPayments().getFirst().getPublicId());
        assertEquals("CARD", response.getPayments().getFirst().getPaymentMethod());
        assertEquals(java.util.List.of("Tarjeta"), response.getPaymentMethods());
    }

    private long measure(Runnable operation) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        operation.run();
        return statistics.getPrepareStatementCount();
    }

    private Order createOrder(OrderStatus status) {
        Order order = new Order();
        order.setTenant(tenant);
        order.setBranch(branch);
        order.setUser(user);
        order.setStatus(status);
        order.setSubtotal(BigDecimal.ZERO);
        order.setTax(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        return orderRepository.save(order);
    }

    private Product createProduct(String name, String price, Integer stock) {
        return createProduct(tenant, name, price, stock);
    }

    private Product createProduct(Tenant productTenant, String name, String price, Integer stock) {
        Product product = new Product();
        product.setTenant(productTenant);
        product.setName(name + " " + UUID.randomUUID());
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setType(ProductType.PRODUCT);
        product.setActive(true);
        product.setDepartment("POS");
        return productRepository.save(product);
    }

    private OrderItem createOrderItem(Order order, Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        item.setStatus(OrderItemStatus.ACTIVE);
        return orderItemRepository.save(item);
    }

    private OrderItemRequest itemRequest(Product product, int quantity) {
        OrderItemRequest request = new OrderItemRequest();
        request.setProductPublicId(product.getPublicId());
        request.setQuantity(quantity);
        return request;
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
