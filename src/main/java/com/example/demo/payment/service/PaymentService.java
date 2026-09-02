package com.example.demo.payment.service;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.common.enums.OrderStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.order.model.Order;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.payment.dto.PaymentRequest;
import com.example.demo.payment.dto.PaymentResponse;
import com.example.demo.payment.model.Payment;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    @Transactional
    public PaymentResponse registerPayment(String orderPublicId, PaymentRequest request) {

        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();
        Long userId = TenantContext.getUserId();

        Order order = orderRepository.findByPublicIdAndTenant_Id(orderPublicId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada"));

        if (order.getStatus() == OrderStatus.CLOSED) {
            throw new IllegalStateException("La orden ya está cerrada");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("La orden está cancelada");
        }

        BigDecimal totalPaidBefore = paymentRepository.sumPaymentsByOrderId(order.getId());
        if (totalPaidBefore == null)
            totalPaidBefore = BigDecimal.ZERO;

        BigDecimal remaining = order.getTotalAmount().subtract(totalPaidBefore);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("La orden ya está pagada completamente");
        }

        BigDecimal amountReceived = request.getAmount();
        BigDecimal change = BigDecimal.ZERO;
        BigDecimal amountToApply;

        if (request.getPaymentMethod() == PaymentMethod.CASH) {
            if (amountReceived.compareTo(remaining) > 0) {
                change = amountReceived.subtract(remaining);
                amountToApply = remaining;
            } else {
                amountToApply = amountReceived;
            }
        } else {
            if (amountReceived.compareTo(remaining) > 0) {
                throw new IllegalArgumentException(
                        "El monto con " + request.getPaymentMethod()
                                + " no puede exceder el restante: $" + remaining);
            }
            amountToApply = amountReceived;
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setTenant(tenant);
        payment.setBranch(branch);
        payment.setUser(user);

        payment.setAmount(amountToApply);

        // NUEVO
        payment.setAmountReceived(amountReceived);
        payment.setChangeAmount(change);

        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setReference(request.getReference());

        paymentRepository.save(payment);

        BigDecimal totalPaidAfter = totalPaidBefore.add(amountToApply);
        BigDecimal newRemaining = order.getTotalAmount().subtract(totalPaidAfter);

        // ✅ SOLO marcar como PARTIALLY_PAID, NO cerrar aquí
        // El frontend se encarga de llamar closeOrder() después
        if (newRemaining.compareTo(BigDecimal.ZERO) > 0) {
            order.setStatus(OrderStatus.PARTIALLY_PAID);
        }
        // Si newRemaining <= 0, dejamos el status actual (OPEN o PARTIALLY_PAID)
        // para que closeOrder() lo cierre correctamente
        orderRepository.save(order);

        return PaymentResponse.builder()
                .orderTotal(order.getTotalAmount())
                .totalPaid(totalPaidAfter)
                .remainingAmount(newRemaining.max(BigDecimal.ZERO))
                .change(change)
                .amountReceived(amountReceived)
                .amountApplied(amountToApply)
                .paymentMethod(request.getPaymentMethod().name())
                .build();
    }
}