package com.example.demo.payment.service;

import com.example.demo.cash.repository.CashRegisterRepository;
import com.example.demo.common.enums.CashStatus;
import com.example.demo.common.enums.OrderStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.order.model.Order;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.payment.dto.PaymentRequest;
import com.example.demo.payment.dto.PaymentResponse;
import com.example.demo.payment.model.Payment;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import io.micrometer.core.annotation.Timed;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CashRegisterRepository cashRegisterRepository;

    @Transactional
    @Timed(value = "spacekids.service.requests", extraTags = {"service", "payment", "operation", "register-order-payment"})
    public PaymentResponse registerPayment(String orderPublicId, PaymentRequest request) {

        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();
        Long userId = TenantContext.getUserId();

        Order order = orderRepository
                .findByPublicIdAndTenant_IdAndBranch_Id(orderPublicId, tenantId, branchId)
                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada"));

        BigDecimal totalPaidBefore = paymentRepository.sumPaymentsByOrderId(order.getId());
        if (totalPaidBefore == null) {
            totalPaidBefore = BigDecimal.ZERO;
        }

        return applyPayment(order, request, null, totalPaidBefore, tenantId, branchId, userId).response();
    }

    /**
     * Shared payment implementation used by the legacy endpoint and transactional checkout.
     * The caller owns the surrounding transaction and supplies the already-known paid total.
     */
    public PaymentApplication applyPayment(
            Order order,
            PaymentRequest request,
            String checkoutRequestId,
            BigDecimal totalPaidBefore,
            Long tenantId,
            Long branchId,
            Long userId) {

        validateRequest(request);

        if (order.getStatus() == OrderStatus.CLOSED) {
            throw new IllegalStateException("La orden ya está cerrada");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("La orden está cancelada");
        }

        cashRegisterRepository.findByTenant_IdAndBranch_IdAndStatus(tenantId, branchId, CashStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("No hay una caja abierta para registrar el pago"));

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

        User user = order.getUser() != null && userId.equals(order.getUser().getId())
                ? order.getUser()
                : userRepository.findByIdAndTenant_IdAndBranch_Id(userId, tenantId, branchId)
                        .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setTenant(order.getTenant());
        payment.setBranch(order.getBranch());
        payment.setUser(user);

        payment.setAmount(amountToApply);

        payment.setAmountReceived(amountReceived);
        payment.setChangeAmount(change);

        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setReference(request.getReference());
        payment.setCheckoutRequestId(checkoutRequestId);

        paymentRepository.save(payment);

        BigDecimal totalPaidAfter = totalPaidBefore.add(amountToApply);
        BigDecimal newRemaining = order.getTotalAmount().subtract(totalPaidAfter);

        if (newRemaining.compareTo(BigDecimal.ZERO) > 0) {
            order.setStatus(OrderStatus.PARTIALLY_PAID);
        }
        orderRepository.save(order);

        PaymentResponse response = PaymentResponse.builder()
                .orderTotal(order.getTotalAmount())
                .totalPaid(totalPaidAfter)
                .remainingAmount(newRemaining.max(BigDecimal.ZERO))
                .change(change)
                .amountReceived(amountReceived)
                .amountApplied(amountToApply)
                .paymentMethod(request.getPaymentMethod().name())
                .build();

        return new PaymentApplication(payment, response, totalPaidAfter);
    }

    public PaymentResponse buildExistingPaymentResponse(
            Order order, Payment payment, BigDecimal totalPaid) {
        BigDecimal amountReceived = payment.getAmountReceived() != null
                ? payment.getAmountReceived()
                : payment.getAmount();
        BigDecimal change = payment.getChangeAmount() != null
                ? payment.getChangeAmount()
                : BigDecimal.ZERO;

        return PaymentResponse.builder()
                .orderTotal(order.getTotalAmount())
                .totalPaid(totalPaid)
                .remainingAmount(order.getTotalAmount().subtract(totalPaid).max(BigDecimal.ZERO))
                .change(change)
                .amountReceived(amountReceived)
                .amountApplied(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod().name())
                .build();
    }

    private void validateRequest(PaymentRequest request) {
        if (request == null || request.getAmount() == null) {
            throw new IllegalArgumentException("El monto es obligatorio");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
        if (request.getPaymentMethod() == null) {
            throw new IllegalArgumentException("El método de pago es obligatorio");
        }
    }

    public record PaymentApplication(
            Payment payment,
            PaymentResponse response,
            BigDecimal totalPaidAfter) {
    }
}
