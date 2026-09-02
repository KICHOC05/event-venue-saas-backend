package com.example.demo.order.service;

import com.example.demo.client.model.Client;
import com.example.demo.client.repository.ClientRepository;
import com.example.demo.loyalty.service.LoyaltyService;
import com.example.demo.security.TenantContext;
import com.example.demo.common.enums.OrderStatus;
import com.example.demo.common.enums.ProductType;
import com.example.demo.common.enums.InventoryMode;
import com.example.demo.common.enums.OrderItemStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.order.dto.*;
import com.example.demo.order.model.Order;
import com.example.demo.order.model.OrderItem;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.payment.model.Payment;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.product.model.Product;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.settings.model.TaxSettings;
import com.example.demo.settings.model.TenantSettings;
import com.example.demo.settings.repository.TaxSettingsRepository;
import com.example.demo.settings.repository.TenantSettingsRepository;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    private final PaymentRepository paymentRepository;
    private final ClientRepository clientRepository;
    private final LoyaltyService loyaltyService;
    private final TaxSettingsRepository taxSettingsRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

    public OrderResponse createOrder(OrderCreateRequest request) {

        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();
        Long userId = TenantContext.getUserId();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Order order = new Order();

        order.setTenant(tenant);
        order.setBranch(branch);
        order.setUser(user);

        order.setCustomerName(request.getCustomerName());

        if (request.getClientPublicId() != null && !request.getClientPublicId().isBlank()) {
            Client client = clientRepository
                    .findByPublicIdAndTenant_Id(request.getClientPublicId(), tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Client not found"));
            order.setClient(client);
            if (order.getCustomerName() == null || order.getCustomerName().isBlank()) {
                order.setCustomerName(client.getParentName());
            }
        }

        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(BigDecimal.ZERO);
        order.setTax(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);

        orderRepository.save(order);

        return mapToResponse(order);
    }

    @Transactional
    public OrderResponse addItem(String orderPublicId, OrderItemRequest request) {

        Long tenantId = TenantContext.getTenantId();

        Order order = getOrderEntity(orderPublicId, tenantId);

        Product product = productRepository
                .findByPublicIdAndTenant_IdAndActiveTrue(
                        request.getProductPublicId(),
                        tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        // REGLA DE NEGOCIO #3: Validar childName para servicios
        if (product.getType() == ProductType.SERVICE) {
            if (!StringUtils.hasText(request.getChildName())) {
                throw new IllegalStateException("Debe capturar el nombre del niño");
            }
        }

        // REGLA DE NEGOCIO #4: Servicios siempre tienen cantidad = 1
        int finalQuantity = request.getQuantity();
        if (product.getType() == ProductType.SERVICE) {
            if (request.getQuantity() != 1) {
                throw new IllegalStateException("Los servicios solo pueden tener cantidad 1");
            }
            finalQuantity = 1;
        }

        TenantSettings settings = tenantSettingsRepository
                .findByTenant_Id(tenantId)
                .orElse(null);

        InventoryMode mode = settings != null
                ? settings.getInventoryMode()
                : InventoryMode.WARNING;

        Integer stock = product.getStock();
        String warning = null;

        if (stock != null && stock < finalQuantity) {

            switch (mode) {

                case STRICT -> throw new IllegalStateException(
                        "Stock insuficiente para el producto: " + product.getName());

                case WARNING -> warning = "Producto vendido sin stock";

                case DISABLED -> {
                }
            }
        }

        OrderItem item = new OrderItem();

        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(finalQuantity);
        item.setUnitPrice(product.getPrice());
        item.setStatus(OrderItemStatus.ACTIVE);

        // REGLA DE NEGOCIO #3: Guardar childName en el OrderItem
        if (product.getType() == ProductType.SERVICE) {
            item.setChildName(request.getChildName());
        }

        // REGLA DE NEGOCIO #6: Timers para servicios
        if (product.getType() == ProductType.SERVICE) {

            LocalDateTime now = LocalDateTime.now();

            item.setSessionStart(now);
            item.setDurationMinutes(product.getDurationMinutes());

            item.setSessionEnd(now.plusMinutes(product.getDurationMinutes()));
            item.setActive(true);
        }

        BigDecimal subtotal = product.getPrice()
                .multiply(BigDecimal.valueOf(finalQuantity));

        item.setSubtotal(subtotal);
        item.setWarning(warning);

        orderItemRepository.save(item);

        if (mode != InventoryMode.DISABLED && product.getStock() != null) {

            product.setStock(product.getStock() - finalQuantity);
            productRepository.save(product);

        }

        recalculateOrder(order);

        return getOrder(orderPublicId);
    }

    @Transactional
    public OrderResponse voidItem(String orderPublicId, String itemPublicId) {

        Long tenantId = TenantContext.getTenantId();

        Order order = getOrderEntity(orderPublicId, tenantId);

        OrderItem item = orderItemRepository
                .findByPublicId(itemPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Item not found"));

        if (!item.getOrder().getPublicId().equals(orderPublicId)) {

            throw new IllegalStateException("El item no pertenece a esta orden");

        }

        if (item.getStatus() == OrderItemStatus.VOIDED) {

            throw new IllegalStateException("El item ya fue anulado");

        }

        Product product = item.getProduct();

        if (product.getStock() != null) {

            product.setStock(product.getStock() + item.getQuantity());
            productRepository.save(product);

        }

        item.setStatus(OrderItemStatus.VOIDED);

        orderItemRepository.save(item);

        recalculateOrder(order);

        return getOrder(orderPublicId);
    }

    @Transactional
    public OrderResponse updateItemQuantity(
            String orderPublicId,
            String itemPublicId,
            UpdateOrderItemRequest request) {

        Long tenantId = TenantContext.getTenantId();

        Order order = getOrderEntity(orderPublicId, tenantId);

        OrderItem item = orderItemRepository
                .findByPublicId(itemPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Item not found"));

        Product product = item.getProduct();

        // REGLA DE NEGOCIO #5: No permitir modificar cantidad de servicios
        if (product.getType() == ProductType.SERVICE) {
            throw new IllegalStateException("Los servicios no permiten modificar cantidad");
        }

        int oldQty = item.getQuantity();
        int newQty = request.getQuantity();
        int difference = newQty - oldQty;

        if (product.getStock() != null) {

            product.setStock(product.getStock() - difference);
            productRepository.save(product);

        }

        item.setQuantity(newQty);

        BigDecimal subtotal = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(newQty));

        item.setSubtotal(subtotal);

        orderItemRepository.save(item);

        recalculateOrder(order);

        return getOrder(orderPublicId);
    }

    @Transactional
    public OrderResponse closeOrder(String publicId) {

        Long tenantId = TenantContext.getTenantId();

        Order order = getOrderEntity(publicId, tenantId);
        log.info("closeOrder called for order={} status={} hasClient={} totalAmount={}",
                order.getPublicId(), order.getStatus(),
                order.getClient() != null, order.getTotalAmount());

        if (order.getStatus() == OrderStatus.CLOSED) {
            log.info("closeOrder SKIP: order {} already CLOSED", order.getPublicId());
            return mapToResponse(order);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede cerrar una orden cancelada");
        }

        BigDecimal paid = paymentRepository.sumPaymentsByOrderId(order.getId());
        if (paid == null)
            paid = BigDecimal.ZERO;

        if (paid.compareTo(order.getTotalAmount()) < 0) {
            throw new IllegalStateException("Pago incompleto");
        }

        order.setStatus(OrderStatus.CLOSED);
        order.setClosedAt(LocalDateTime.now());

        orderRepository.save(order);

        log.info("closeOrder: order {} closed, registering loyalty visits...", order.getPublicId());

        try {
            loyaltyService.registerVisits(order);
        } catch (Exception e) {
            log.warn("Error registering loyalty visits for order {}: {}", order.getPublicId(), e.getMessage(), e);
        }

        return mapToResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(String publicId) {

        Long tenantId = TenantContext.getTenantId();

        Order order = getOrderEntity(publicId, tenantId);

        List<OrderItem> items = orderItemRepository.findAllByOrder_Id(order.getId());

        for (OrderItem item : items) {

            if (item.getStatus() == OrderItemStatus.ACTIVE) {

                Product product = item.getProduct();

                if (product.getStock() != null) {

                    product.setStock(product.getStock() + item.getQuantity());
                    productRepository.save(product);

                }
            }
        }

        order.setStatus(OrderStatus.CANCELLED);

        orderRepository.save(order);

        return mapToResponse(order);
    }

    public OrderResponse getOrder(String publicId) {

        Long tenantId = TenantContext.getTenantId();

        Order order = getOrderEntity(publicId, tenantId);

        return mapToResponse(order);
    }

    private Order getOrderEntity(String publicId, Long tenantId) {

        return orderRepository
                .findByPublicIdAndTenant_Id(publicId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    }

    private void recalculateOrder(Order order) {

        Long tenantId = TenantContext.getTenantId();

        TaxSettings taxSettings = taxSettingsRepository
                .findByTenant_Id(tenantId)
                .orElse(null);

        BigDecimal subtotal = orderItemRepository
                .findAllByOrder_Id(order.getId())
                .stream()
                .filter(item -> item.getStatus() == OrderItemStatus.ACTIVE)
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tax = BigDecimal.ZERO;

        if (taxSettings != null && Boolean.TRUE.equals(taxSettings.getTaxEnabled())) {

            tax = subtotal.multiply(taxSettings.getTaxRate());

        }

        BigDecimal total = subtotal.add(tax);

        order.setSubtotal(subtotal);
        order.setTax(tax);
        order.setTotalAmount(total);

        orderRepository.save(order);
    }

    private OrderResponse mapToResponse(Order order) {

        List<OrderItem> allItems = orderItemRepository
                .findAllByOrder_Id(order.getId());

        List<OrderItemResponse> items = allItems
                .stream()
                .map(item -> {

                    OrderItemResponse response = new OrderItemResponse();

                    response.setPublicId(item.getPublicId());
                    response.setProductPublicId(item.getProduct().getPublicId());
                    response.setProductName(item.getProduct().getName());
                    response.setQuantity(item.getQuantity());
                    response.setUnitPrice(item.getUnitPrice());
                    response.setSubtotal(item.getSubtotal());
                    response.setWarning(item.getWarning());
                    response.setStatus(item.getStatus().name());
                    response.setDurationMinutes(item.getDurationMinutes());
                    response.setActive(item.getActive());
                    response.setSessionStart(item.getSessionStart());
                    response.setSessionEnd(item.getSessionEnd());
                    // REGLA DE NEGOCIO #2: Mapear childName desde OrderItem
                    response.setChildName(item.getChildName());

                    return response;

                }).toList();

        OrderResponse response = new OrderResponse();

        response.setPublicId(order.getPublicId());
        response.setOrderNumber(order.getId());
        response.setShortCode(formatOrderNumber(order.getId()));
        response.setStatus(order.getStatus());
        response.setCustomerName(order.getCustomerName());
        // REMOVED: order.getChildName() ya no existe en Order
        response.setSubtotal(order.getSubtotal());
        response.setTax(order.getTax());
        response.setTotalAmount(order.getTotalAmount());
        response.setCreatedAt(order.getCreatedAt());
        response.setClosedAt(order.getClosedAt());
        response.setItems(items);

        if (order.getUser() != null) {
            response.setSellerName(order.getUser().getName());
            response.setSellerPublicId(order.getUser().getPublicId());
            response.setSellerEmail(order.getUser().getEmail());
        }

        response.setBranchPublicId(order.getBranch().getPublicId());
        response.setBranchName(order.getBranch().getName());

        List<Payment> payments = paymentRepository.findAllByOrder_IdOrderByCreatedAtAscIdAsc(order.getId());
        List<String> paymentMethods = payments.stream()
                .map(Payment::getPaymentMethod)
                .map(method -> switch (method) {
                    case CASH -> "Efectivo";
                    case CARD -> "Tarjeta";
                    case TRANSFER -> "Transferencia";
                })
                .distinct()
                .collect(Collectors.toList());
        response.setPaymentMethods(paymentMethods);
        response.setPayments(payments.stream()
                .map(payment -> OrderPaymentDetailResponse.builder()
                        .publicId(payment.getPublicId())
                        .paymentMethod(payment.getPaymentMethod().name())
                        .amount(payment.getAmount())
                        .amountReceived(payment.getAmountReceived())
                        .changeAmount(payment.getChangeAmount())
                        .reference(payment.getReference())
                        .createdAt(payment.getCreatedAt())
                        .receivedByPublicId(payment.getUser().getPublicId())
                        .receivedByName(payment.getUser().getName())
                        .receivedByEmail(payment.getUser().getEmail())
                        .build())
                .toList());

        Set<String> childNames = new LinkedHashSet<>();
        for (OrderItem item : allItems) {
            if (item.getStatus() == OrderItemStatus.ACTIVE
                    && item.getChildName() != null && !item.getChildName().trim().isEmpty()) {
                childNames.add(item.getChildName());
            }
        }
        response.setChildNames(new ArrayList<>(childNames));

        if (order.getClient() != null) {
            response.setClientPublicId(order.getClient().getPublicId());
            response.setClientParentName(order.getClient().getParentName());
        }

        return response;
    }

    @Transactional(readOnly = true)
    public Page<OrderHistoryResponse> getOrderHistory(
            int page, int size,
            String search, String orderNumber, String status,
            PaymentMethod paymentMethod, String userPublicId, String branchPublicId,
            String createdAtFrom, String createdAtTo) {

        Long tenantId = TenantContext.getTenantId();

        OrderStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            statusEnum = OrderStatus.valueOf(status);
        }

        LocalDateTime from = parseDateBoundary(createdAtFrom, false);
        LocalDateTime to = parseDateBoundary(createdAtTo, true);
        Long numericOrderNumber = parseOrderNumber(orderNumber);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<Order> orderPage = orderRepository.findHistoryByTenant(
                tenantId, blankToNull(branchPublicId), blankToNull(userPublicId),
                numericOrderNumber, paymentMethod, statusEnum, from, to,
                blankToNull(search), pageable);

        if (orderPage.isEmpty()) return Page.empty(pageable);

        List<Long> orderIds = orderPage.getContent().stream().map(Order::getId).toList();
        Map<Long, List<Payment>> paymentsByOrder = paymentRepository.findAllByOrderIds(orderIds)
                .stream().collect(Collectors.groupingBy(payment -> payment.getOrder().getId()));
        Map<Long, List<OrderItem>> itemsByOrder = orderItemRepository.findAllByOrderIdsWithProduct(orderIds)
                .stream().collect(Collectors.groupingBy(item -> item.getOrder().getId()));

        List<OrderHistoryResponse> content = orderPage.getContent().stream()
                .map(order -> mapToHistoryResponse(
                        order,
                        paymentsByOrder.getOrDefault(order.getId(), List.of()),
                        itemsByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
        return new PageImpl<>(content, pageable, orderPage.getTotalElements());
    }

    private OrderHistoryResponse mapToHistoryResponse(
            Order order, List<Payment> payments, List<OrderItem> allItems) {
        OrderHistoryResponse r = new OrderHistoryResponse();

        r.setPublicId(order.getPublicId());
        r.setOrderNumber(order.getId());
        r.setShortCode(formatOrderNumber(order.getId()));
        r.setCreatedAt(order.getCreatedAt());
        r.setClosedAt(order.getClosedAt());
        r.setCustomerName(order.getCustomerName());
        r.setStatus(order.getStatus().name());
        r.setTotalAmount(order.getTotalAmount());

        if (order.getClient() != null) {
            r.setClientPublicId(order.getClient().getPublicId());
            r.setClientParentName(order.getClient().getParentName());
            if (r.getCustomerName() == null || r.getCustomerName().isBlank()) {
                r.setCustomerName(order.getClient().getParentName());
            }
        }

        if (order.getUser() != null) {
            r.setSellerName(order.getUser().getName());
            r.setSellerPublicId(order.getUser().getPublicId());
            r.setSellerEmail(order.getUser().getEmail());
        }

        r.setBranchPublicId(order.getBranch().getPublicId());
        r.setBranchName(order.getBranch().getName());

        List<String> paymentMethods = payments.stream()
                .map(p -> p.getPaymentMethod())
                .map(method -> switch (method) {
                    case CASH -> "Efectivo";
                    case CARD -> "Tarjeta";
                    case TRANSFER -> "Transferencia";
                })
                .distinct()
                .collect(Collectors.toList());
        r.setPaymentMethods(paymentMethods);

        List<OrderItem> activeItems = allItems.stream()
                .filter(i -> i.getStatus() == OrderItemStatus.ACTIVE)
                .collect(Collectors.toList());

        r.setItemsCount((long) activeItems.size());

        Set<String> childNames = new LinkedHashSet<>();
        for (OrderItem item : activeItems) {
            if (item.getChildName() != null && !item.getChildName().trim().isEmpty()) {
                childNames.add(item.getChildName());
            }
        }
        r.setChildNames(new ArrayList<>(childNames));

        return r;
    }

    private String formatOrderNumber(Long orderNumber) {
        return "Orden #" + String.format("%06d", orderNumber);
    }

    private Long parseOrderNumber(String value) {
        if (value == null || value.isBlank()) return null;
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) throw new IllegalArgumentException("Número de orden inválido");
        return Long.valueOf(digits);
    }

    private LocalDateTime parseDateBoundary(String value, boolean upperExclusive) {
        if (value == null || value.isBlank()) return null;
        if (value.length() == 10) {
            java.time.LocalDate date = java.time.LocalDate.parse(value);
            return upperExclusive ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
        }
        LocalDateTime parsed = LocalDateTime.parse(value);
        return upperExclusive ? parsed.plusNanos(1) : parsed;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public List<ActiveSessionResponse> getActiveSessions() {

        Long tenantId = TenantContext.getTenantId();

        LocalDateTime now = LocalDateTime.now();

        List<OrderItem> items =
                orderItemRepository
                        .findByActiveTrueAndOrder_Tenant_Id(tenantId);

        return items.stream()
                .map(item -> {

                    ActiveSessionResponse response =
                            new ActiveSessionResponse();

                    long seconds = 0;

                    if (item.getSessionEnd() != null) {

                        Duration remaining =
                                Duration.between(
                                        now,
                                        item.getSessionEnd());

                        seconds =
                                Math.max(
                                        remaining.getSeconds(),
                                        0);
                    }

                    response.setItemPublicId(item.getPublicId());

                    // REGLA DE NEGOCIO #2: Obtener childName de OrderItem
                    response.setChildName(item.getChildName());

                    response.setProductName(
                            item.getProduct().getName());

                    response.setSessionStart(
                            item.getSessionStart());

                    response.setSessionEnd(
                            item.getSessionEnd());

                    response.setRemainingSeconds(seconds);

                    response.setRemainingMinutes(
                            seconds / 60);

                    response.setExpiringSoon(
                            seconds <= 300);
                            response.setExpired(seconds <= 0);

                    response.setStatus(
                            item.getStatus().name());

                    return response;

                })
                .toList();
    }

    public List<ActiveSessionResponse> getSessionHistory() {

        Long tenantId = TenantContext.getTenantId();

        List<OrderItem> items =
                orderItemRepository
                        .findByStatusAndOrder_Tenant_Id(
                                OrderItemStatus.FINISHED,
                                tenantId);

        return items.stream()
                .map(item -> {

                    ActiveSessionResponse response =
                            new ActiveSessionResponse();

                    response.setItemPublicId(
                            item.getPublicId());

                    // REGLA DE NEGOCIO #2: Obtener childName de OrderItem
                    response.setChildName(item.getChildName());

                    response.setProductName(
                            item.getProduct().getName());

                    response.setSessionStart(
                            item.getSessionStart());

                    response.setSessionEnd(
                            item.getSessionEnd());

                    response.setStatus(
                            item.getStatus().name());

                    return response;

                })
                .toList();
    }

    public TimerDashboardResponse getTimersDashboard() {

        Long tenantId = TenantContext.getTenantId();

        LocalDateTime now = LocalDateTime.now();

        List<OrderItem> activeItems =
                orderItemRepository
                        .findByActiveTrueAndOrder_Tenant_Id(
                                tenantId);

        long expiringSoon =
                activeItems.stream()
                        .filter(item ->
                                item.getSessionEnd() != null
                                        && Duration.between(
                                                now,
                                                item.getSessionEnd())
                                                .getSeconds() <= 300)
                        .count();

        LocalDateTime startOfDay =
                LocalDateTime.now()
                        .toLocalDate()
                        .atStartOfDay();

        LocalDateTime endOfDay =
                startOfDay.plusDays(1);

        long finishedToday =
                orderItemRepository
                        .findByStatusAndOrder_Tenant_IdAndSessionStartBetween(
                                OrderItemStatus.FINISHED,
                                tenantId,
                                startOfDay,
                                endOfDay)
                        .size();

        TimerDashboardResponse response =
                new TimerDashboardResponse();

        response.setActiveSessions(
                (long) activeItems.size());

        response.setExpiringSoon(
                expiringSoon);

        response.setFinishedToday(
                finishedToday);

        return response;
    }
}
