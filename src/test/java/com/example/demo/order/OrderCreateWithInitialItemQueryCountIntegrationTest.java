package com.example.demo.order;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.common.enums.InventoryMode;
import com.example.demo.common.enums.ProductType;
import com.example.demo.common.enums.UserRole;
import com.example.demo.order.dto.OrderCreateRequest;
import com.example.demo.order.dto.OrderItemRequest;
import com.example.demo.order.dto.OrderResponse;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.order.service.OrderService;
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
        "spring.datasource.url=jdbc:h2:mem:order-create-initial-item-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("test")
class OrderCreateWithInitialItemQueryCountIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private TenantSettingsRepository tenantSettingsRepository;
    @Autowired private TaxSettingsRepository taxSettingsRepository;
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
        tenant.setBusinessName("Tenant create " + UUID.randomUUID());
        tenant = tenantRepository.save(tenant);

        branch = new Branch();
        branch.setTenant(tenant);
        branch.setName("Sucursal create " + UUID.randomUUID());
        branch = branchRepository.save(branch);

        user = new User();
        user.setTenant(tenant);
        user.setBranch(branch);
        user.setName("Cajero create");
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
    void createOrderWithoutInitialItemRemainsCompatibleAndUsesFourStatements() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setCustomerName("Cliente baseline");

        long statements = measure(() -> {
            OrderResponse response = orderService.createOrder(request);
            assertEquals(0, response.getItems().size());
            assertEquals(0, response.getPayments().size());
        });

        assertEquals(4, statements);
    }

    @Test
    void oldCreateThenAddItemFlowRemainsCompatibleAndUsesFourteenStatements() {
        Product product = createProduct("Producto baseline", "10.00", 20);
        OrderCreateRequest createRequest = new OrderCreateRequest();
        createRequest.setCustomerName("Cliente baseline");
        OrderItemRequest itemRequest = itemRequest(product, 1);

        long statements = measure(() -> {
            OrderResponse created = orderService.createOrder(createRequest);
            OrderResponse updated = orderService.addItem(created.getPublicId(), itemRequest);
            assertEquals(1, updated.getItems().size());
        });

        assertEquals(14, statements);
    }

    @Test
    void combinedCreateWithInventoryUsesElevenStatementsAndReturnsCalculatedOrder() {
        Product product = createProduct("Producto combinado", "10.00", 20);
        OrderCreateRequest request = createRequestWithInitialItem(product, 1);

        long statements = measure(() -> {
            OrderResponse response = orderService.createOrder(request);
            assertEquals(1, response.getItems().size());
            assertEquals(product.getPublicId(), response.getItems().getFirst().getProductPublicId());
            assertEquals(1, response.getItems().getFirst().getQuantity());
            assertAmount("10.00", response.getSubtotal());
            assertAmount("1.60", response.getTax());
            assertAmount("11.60", response.getTotalAmount());
            assertEquals(0, response.getPayments().size());
        });

        assertEquals(11, statements);
        assertEquals(19, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void combinedCreateWithoutInventoryUsesTenStatements() {
        Product product = createProduct("Producto combinado sin stock", "10.00", null);
        OrderCreateRequest request = createRequestWithInitialItem(product, 1);

        long statements = measure(() -> {
            OrderResponse response = orderService.createOrder(request);
            assertEquals(1, response.getItems().size());
        });

        assertEquals(10, statements);
    }

    @Test
    void combinedCreateSupportsQuantityGreaterThanOne() {
        Product product = createProduct("Producto multiple", "7.50", 20);

        OrderResponse response = orderService.createOrder(createRequestWithInitialItem(product, 3));

        assertEquals(3, response.getItems().getFirst().getQuantity());
        assertAmount("22.50", response.getSubtotal());
        assertAmount("3.60", response.getTax());
        assertAmount("26.10", response.getTotalAmount());
        assertEquals(17, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void insufficientStrictStockRollsBackOrderAndPreservesStock() {
        TenantSettings settings = tenantSettingsRepository.findByTenant_Id(tenant.getId()).orElseThrow();
        settings.setInventoryMode(InventoryMode.STRICT);
        tenantSettingsRepository.save(settings);
        Product product = createProduct("Stock insuficiente", "10.00", 1);
        long ordersBefore = orderRepository.count();

        assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(createRequestWithInitialItem(product, 2)));

        assertEquals(ordersBefore, orderRepository.count());
        assertEquals(1, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void productFromAnotherTenantRollsBackTheNewOrder() {
        Tenant otherTenant = new Tenant();
        otherTenant.setBusinessName("Tenant ajeno " + UUID.randomUUID());
        otherTenant = tenantRepository.save(otherTenant);
        Product foreignProduct = createProduct(otherTenant, "Producto ajeno", "10.00", 10, true, ProductType.PRODUCT);
        long ordersBefore = orderRepository.count();

        assertThrows(EntityNotFoundException.class,
                () -> orderService.createOrder(createRequestWithInitialItem(foreignProduct, 1)));

        assertEquals(ordersBefore, orderRepository.count());
        assertEquals(10, productRepository.findById(foreignProduct.getId()).orElseThrow().getStock());
    }

    @Test
    void branchThatDoesNotBelongToTheCurrentUserCannotCreateAnOrder() {
        Branch otherBranch = new Branch();
        otherBranch.setTenant(tenant);
        otherBranch.setName("Sucursal incorrecta " + UUID.randomUUID());
        otherBranch = branchRepository.save(otherBranch);
        TenantContext.set(new TenantContext.TenantInfo(
                tenant.getId(), otherBranch.getId(), user.getId(), UserRole.ADMIN));
        Product product = createProduct("Producto sucursal", "10.00", 10);
        long ordersBefore = orderRepository.count();

        assertThrows(EntityNotFoundException.class,
                () -> orderService.createOrder(createRequestWithInitialItem(product, 1)));

        assertEquals(ordersBefore, orderRepository.count());
        assertEquals(10, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void inactiveProductRollsBackTheNewOrder() {
        Product product = createProduct(tenant, "Producto inactivo", "10.00", 10, false, ProductType.PRODUCT);
        long ordersBefore = orderRepository.count();

        assertThrows(EntityNotFoundException.class,
                () -> orderService.createOrder(createRequestWithInitialItem(product, 1)));

        assertEquals(ordersBefore, orderRepository.count());
        assertEquals(10, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void itemValidationErrorRollsBackTheNewOrder() {
        Product service = createProduct(tenant, "Servicio sin niño", "10.00", null, true, ProductType.SERVICE);
        service.setDurationMinutes(60);
        Product savedService = productRepository.save(service);
        long ordersBefore = orderRepository.count();

        assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(createRequestWithInitialItem(savedService, 1)));

        assertEquals(ordersBefore, orderRepository.count());
    }

    @Test
    void invalidQuantityRollsBackTheNewOrder() {
        Product product = createProduct("Cantidad inválida", "10.00", 10);
        long ordersBefore = orderRepository.count();

        assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(createRequestWithInitialItem(product, 0)));

        assertEquals(ordersBefore, orderRepository.count());
        assertEquals(10, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    private long measure(Runnable operation) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        operation.run();
        return statistics.getPrepareStatementCount();
    }

    private Product createProduct(String name, String price, Integer stock) {
        return createProduct(tenant, name, price, stock, true, ProductType.PRODUCT);
    }

    private Product createProduct(
            Tenant productTenant,
            String name,
            String price,
            Integer stock,
            boolean active,
            ProductType type) {
        Product product = new Product();
        product.setTenant(productTenant);
        product.setName(name + " " + UUID.randomUUID());
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setType(type);
        product.setActive(active);
        product.setDepartment("POS");
        return productRepository.save(product);
    }

    private OrderItemRequest itemRequest(Product product, int quantity) {
        OrderItemRequest request = new OrderItemRequest();
        request.setProductPublicId(product.getPublicId());
        request.setQuantity(quantity);
        return request;
    }

    private OrderCreateRequest createRequestWithInitialItem(Product product, int quantity) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setCustomerName("Cliente combinado");
        request.setInitialItem(itemRequest(product, quantity));
        return request;
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
