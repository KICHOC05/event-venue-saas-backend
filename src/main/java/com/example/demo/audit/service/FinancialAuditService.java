package com.example.demo.audit.service;

import com.example.demo.audit.dto.FinancialAuditEntryResponse;
import com.example.demo.audit.dto.FinancialAuditSource;
import com.example.demo.cash.model.CashMovement;
import com.example.demo.cash.repository.CashMovementRepository;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.event.model.EventPayment;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.payment.model.Payment;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinancialAuditService {

    private final PaymentRepository paymentRepository;
    private final EventPaymentRepository eventPaymentRepository;
    private final CashMovementRepository cashMovementRepository;

    @Transactional(readOnly = true)
    public Page<FinancialAuditEntryResponse> getAudit(
            int page,
            int size,
            FinancialAuditSource source,
            PaymentMethod paymentMethod,
            String userPublicId,
            String branchPublicId,
            LocalDate from,
            LocalDate to) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int requested = Math.addExact(Math.multiplyExact(safePage, safeSize), safeSize);
        Pageable top = PageRequest.of(0, requested);
        LocalDateTime fromDate = from != null ? from.atStartOfDay() : null;
        LocalDateTime toExclusive = to != null ? to.plusDays(1).atStartOfDay() : null;
        Long tenantId = TenantContext.getTenantId();

        List<FinancialAuditEntryResponse> entries = new ArrayList<>();
        long total = 0;

        if (source == null || source == FinancialAuditSource.POS) {
            Page<Payment> payments = paymentRepository.findForAudit(
                    tenantId, blankToNull(branchPublicId), paymentMethod,
                    blankToNull(userPublicId), fromDate, toExclusive, top);
            total += payments.getTotalElements();
            payments.forEach(payment -> entries.add(fromPosPayment(payment)));
        }

        if (source == null || source == FinancialAuditSource.EVENT) {
            Page<EventPayment> eventPayments = eventPaymentRepository.findForAudit(
                    tenantId, blankToNull(branchPublicId), paymentMethod,
                    blankToNull(userPublicId), fromDate, toExclusive, top);
            total += eventPayments.getTotalElements();
            eventPayments.forEach(payment -> entries.add(fromEventPayment(payment)));
        }

        if ((source == null || source == FinancialAuditSource.MOVEMENT) && paymentMethod == null) {
            Page<CashMovement> movements = cashMovementRepository.findForAudit(
                    tenantId, blankToNull(branchPublicId), blankToNull(userPublicId),
                    fromDate, toExclusive, top);
            total += movements.getTotalElements();
            movements.forEach(movement -> entries.add(fromMovement(movement)));
        }

        entries.sort(Comparator
                .comparing(FinancialAuditEntryResponse::getDate,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FinancialAuditEntryResponse::getSource)
                .thenComparing(FinancialAuditEntryResponse::getEntryPublicId));

        int start = Math.min(safePage * safeSize, entries.size());
        int end = Math.min(start + safeSize, entries.size());
        return new PageImpl<>(entries.subList(start, end),
                PageRequest.of(safePage, safeSize), total);
    }

    private FinancialAuditEntryResponse fromPosPayment(Payment payment) {
        return FinancialAuditEntryResponse.builder()
                .source(FinancialAuditSource.POS.name())
                .type("PAYMENT")
                .reference(formatOrderNumber(payment.getOrder().getId()))
                .date(payment.getCreatedAt())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod().name())
                .userPublicId(payment.getUser().getPublicId())
                .userName(payment.getUser().getName())
                .userEmail(payment.getUser().getEmail())
                .branchPublicId(payment.getBranch().getPublicId())
                .branchName(payment.getBranch().getName())
                .operationPublicId(payment.getOrder().getPublicId())
                .entryPublicId(payment.getPublicId())
                .build();
    }

    private FinancialAuditEntryResponse fromEventPayment(EventPayment payment) {
        String userEmail = payment.getReceivedByUserEmail();
        return FinancialAuditEntryResponse.builder()
                .source(FinancialAuditSource.EVENT.name())
                .type("PAYMENT")
                .reference(formatEventNumber(payment.getEventBooking().getEventNumber()))
                .date(payment.getPaidAt())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod().name())
                .userPublicId(payment.getReceivedByUserPublicId())
                .userName(userEmail)
                .userEmail(userEmail)
                .branchPublicId(payment.getBranch().getPublicId())
                .branchName(payment.getBranch().getName())
                .operationPublicId(payment.getEventBooking().getPublicId())
                .entryPublicId(payment.getPublicId())
                .cashRegisterPublicId(payment.getCashRegister() != null
                        ? payment.getCashRegister().getPublicId() : null)
                .build();
    }

    private FinancialAuditEntryResponse fromMovement(CashMovement movement) {
        return FinancialAuditEntryResponse.builder()
                .source(FinancialAuditSource.MOVEMENT.name())
                .type(movement.getType().name())
                .reference(movement.getReason())
                .date(movement.getCreatedAt())
                .amount(movement.getType().name().equals("WITHDRAWAL")
                        ? movement.getAmount().negate() : movement.getAmount())
                .userPublicId(movement.getUser().getPublicId())
                .userName(movement.getUser().getName())
                .userEmail(movement.getUser().getEmail())
                .branchPublicId(movement.getBranch().getPublicId())
                .branchName(movement.getBranch().getName())
                .operationPublicId(movement.getCashRegister().getPublicId())
                .entryPublicId(movement.getPublicId())
                .cashRegisterPublicId(movement.getCashRegister().getPublicId())
                .build();
    }

    private String formatOrderNumber(Long number) {
        return "Orden #" + String.format("%06d", number);
    }

    private String formatEventNumber(Long number) {
        return number == null ? "Evento histórico" : "EV-" + String.format("%06d", number);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
