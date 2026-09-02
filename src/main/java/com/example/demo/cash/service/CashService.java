package com.example.demo.cash.service;

import com.example.demo.cash.dto.*;
import com.example.demo.cash.model.CashMovement;
import com.example.demo.cash.model.CashRegister;
import com.example.demo.cash.repository.CashMovementRepository;
import com.example.demo.cash.repository.CashRegisterRepository;
import com.example.demo.common.enums.CashMovementType;
import com.example.demo.common.enums.CashStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.payment.repository.PaymentRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CashService {

    private final CashRegisterRepository cashRegisterRepository;
    private final CashMovementRepository cashMovementRepository;
    private final PaymentRepository paymentRepository;
    private final EventPaymentRepository eventPaymentRepository;
    private final OrderRepository orderRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final CashSettingsService cashSettingsService;

    private record CashSummary(
            BigDecimal openingAmount,
            BigDecimal posCashSales,
            BigDecimal eventCashPayments,
            BigDecimal cashSales,
            BigDecimal posCardSales,
            BigDecimal eventCardPayments,
            BigDecimal cardSales,
            BigDecimal posTransferSales,
            BigDecimal eventTransferPayments,
            BigDecimal transferSales,
            BigDecimal depositTotal,
            BigDecimal withdrawalTotal,
            BigDecimal expectedCash) {
    }

    @Transactional
    public CashRegisterResponse openCash(OpenCashRequest request) {

        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();
        Long userId = TenantContext.getUserId();

        cashRegisterRepository
                .findByBranch_IdAndStatus(branchId, CashStatus.OPEN)
                .ifPresent(c -> {
                    throw new IllegalStateException("Ya existe una caja abierta");
                });

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        CashRegister cash = new CashRegister();
        cash.setTenant(tenant);
        cash.setBranch(branch);
        cash.setOpenedBy(user);
        cash.setOpeningAmount(cashSettingsService.getDefaultOpeningAmount(branchId));
        cash.setOpenedAt(LocalDateTime.now());
        cash.setStatus(CashStatus.OPEN);

        cashRegisterRepository.save(cash);

        CashSummary summary = calculateCashSummary(cash);
        return buildResponse(cash, summary);
    }

    public CashRegisterResponse currentCash() {
        CashRegister cash = getOpenCashRegister();
        CashSummary summary = calculateCashSummary(cash);
        return buildResponse(cash, summary);
    }

    @Transactional
    public CashRegisterResponse closeCash(CloseCashRequest request) {

        if (request.getCountedCash() == null) {
            throw new IllegalArgumentException("countedCash es obligatorio");
        }

        Long userId = TenantContext.getUserId();
        CashRegister cash = getOpenCashRegisterForUpdate();

        CashSummary summary = calculateCashSummary(cash);

        BigDecimal difference = request.getCountedCash().subtract(summary.expectedCash());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        cash.setClosingAmount(request.getCountedCash());
        cash.setExpectedAmount(summary.expectedCash());
        cash.setDifference(difference);
        cash.setClosedAt(LocalDateTime.now());
        cash.setClosedBy(user);
        cash.setStatus(CashStatus.CLOSED);

        cashRegisterRepository.save(cash);

        return buildResponse(cash, summary);
    }

    @Transactional
    public CashMovementResponse withdraw(CashMovementRequest request) {
        return createMovement(request, CashMovementType.WITHDRAWAL);
    }

    @Transactional
    public CashMovementResponse deposit(CashMovementRequest request) {
        return createMovement(request, CashMovementType.DEPOSIT);
    }

    @Transactional
    public CashMovementResponse voidMovement(String publicId, CashMovementVoidRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();
        Long userId = TenantContext.getUserId();

        CashMovement movement = cashMovementRepository
                .findByPublicIdAndTenant_IdAndBranch_Id(publicId, tenantId, branchId)
                .orElseThrow(() -> new EntityNotFoundException("Movimiento no encontrado"));

        if (Boolean.TRUE.equals(movement.getVoided())) {
            throw new IllegalStateException("El movimiento ya está anulado");
        }

        if (movement.getCashRegister().getStatus() == CashStatus.CLOSED) {
            throw new IllegalStateException(
                    "No se pueden anular movimientos de una caja cerrada");
        }

        User voidedBy = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        movement.setVoided(true);
        movement.setVoidedAt(LocalDateTime.now());
        movement.setVoidedBy(voidedBy);
        movement.setVoidReason(request.getReason());

        cashMovementRepository.save(movement);

        return mapMovementToResponse(movement);
    }

    public CashMovementResponse getMovementDetail(String publicId) {
        Long tenantId = TenantContext.getTenantId();

        CashMovement movement = cashMovementRepository
                .findByPublicIdAndTenant_Id(publicId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Movimiento no encontrado"));

        return mapMovementToResponse(movement);
    }

    private CashMovementResponse createMovement(CashMovementRequest request, CashMovementType type) {
        CashRegister cash = getOpenCashRegisterForUpdate();

        if (type == CashMovementType.WITHDRAWAL) {
            CashSummary summary = calculateCashSummary(cash);

            if (request.getAmount().compareTo(summary.expectedCash()) > 0) {
                throw new IllegalStateException(
                        "Fondos insuficientes. Disponible: " + summary.expectedCash());
            }
        }

        Long userId = TenantContext.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        CashMovement movement = new CashMovement();
        movement.setTenant(cash.getTenant());
        movement.setBranch(cash.getBranch());
        movement.setCashRegister(cash);
        movement.setUser(user);
        movement.setType(type);
        movement.setAmount(request.getAmount());
        movement.setReason(request.getReason());
        movement.setNotes(request.getNotes());
        movement.setVoided(false);

        cashMovementRepository.save(movement);

        return mapMovementToResponse(movement);
    }

    public List<CashMovementResponse> getCurrentMovements() {
        CashRegister cash = getOpenCashRegister();
        return cashMovementRepository.findByCashRegister_IdOrderByCreatedAtDesc(cash.getId())
                .stream()
                .map(this::mapMovementToResponse)
                .collect(Collectors.toList());
    }

    public Page<CashMovementResponse> getMovementHistory(
            int page, int size,
            CashMovementType type, Boolean voided,
            String userPublicId,
            String branchPublicId, String cashRegisterPublicId,
            String from, String to) {

        Long tenantId = TenantContext.getTenantId();
        Pageable pageable = historyPage(page, size, "createdAt");

        return cashMovementRepository.findHistoryByBranch(
                        tenantId, blankToNull(branchPublicId), blankToNull(cashRegisterPublicId),
                        type, voided, blankToNull(userPublicId),
                        parseDateBoundary(from, false), parseDateBoundary(to, true), pageable)
                .map(this::mapMovementToResponse);
    }

    public Page<CashRegisterHistoryResponse> getCashRegisterHistory(
            int page, int size,
            CashStatus status, String openedByPublicId,
            String branchPublicId, String from, String to) {

        Long tenantId = TenantContext.getTenantId();
        Pageable pageable = historyPage(page, size, "openedAt");

        Page<CashRegister> cashPage = cashRegisterRepository.findHistoryByBranch(
                        tenantId, blankToNull(branchPublicId), status, blankToNull(openedByPublicId),
                        parseDateBoundary(from, false), parseDateBoundary(to, true), pageable);
        if (cashPage.isEmpty()) return Page.empty(pageable);

        List<Long> cashIds = cashPage.getContent().stream().map(CashRegister::getId).toList();
        CashHistoryBatch batch = loadCashSummaries(cashPage.getContent(), cashIds);
        Map<Long, Long> orderCounts = new HashMap<>();
        orderRepository.countByCashRegisters(cashIds)
                .forEach(row -> orderCounts.put((Long) row[0], (Long) row[1]));

        List<CashRegisterHistoryResponse> content = cashPage.getContent().stream()
                .map(cash -> mapToCashRegisterHistoryResponse(
                        cash, batch.summaries().get(cash.getId()),
                        orderCounts.getOrDefault(cash.getId(), 0L),
                        batch.movementCounts().getOrDefault(cash.getId(), 0L)))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(
                content, pageable, cashPage.getTotalElements());
    }

    public CashRegisterDetailResponse getCashRegisterDetail(String publicId) {
        Long tenantId = TenantContext.getTenantId();

        CashRegister cash = cashRegisterRepository
                .findByPublicIdAndTenant_Id(publicId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Caja no encontrada"));

        return mapToCashRegisterDetailResponse(cash);
    }

    private CashRegisterHistoryResponse mapToCashRegisterHistoryResponse(
            CashRegister cash, CashSummary summary, long orderCount, long movementCount) {

        return CashRegisterHistoryResponse.builder()
                .publicId(cash.getPublicId())
                .status(cash.getStatus().name())
                .openingAmount(cash.getOpeningAmount())
                .closingAmount(cash.getClosingAmount())
                .expectedAmount(cash.getStatus() == CashStatus.CLOSED && cash.getExpectedAmount() != null
                        ? cash.getExpectedAmount() : summary.expectedCash())
                .difference(cash.getDifference())
                .openedAt(cash.getOpenedAt())
                .closedAt(cash.getClosedAt())
                .openedByName(cash.getOpenedBy().getName())
                .openedByPublicId(cash.getOpenedBy().getPublicId())
                .openedByEmail(cash.getOpenedBy().getEmail())
                .closedByName(cash.getClosedBy() != null
                        ? cash.getClosedBy().getName() : null)
                .closedByPublicId(cash.getClosedBy() != null ? cash.getClosedBy().getPublicId() : null)
                .closedByEmail(cash.getClosedBy() != null ? cash.getClosedBy().getEmail() : null)
                .branchPublicId(cash.getBranch().getPublicId())
                .branchName(cash.getBranch().getName())
                .cashSales(summary.cashSales())
                .cardSales(summary.cardSales())
                .transferSales(summary.transferSales())
                .depositTotal(summary.depositTotal())
                .withdrawalTotal(summary.withdrawalTotal())
                .orderCount(orderCount)
                .movementCount((int) movementCount)
                .build();
    }

    private record CashHistoryBatch(
            Map<Long, CashSummary> summaries,
            Map<Long, Long> movementCounts) {}

    private CashHistoryBatch loadCashSummaries(
            List<CashRegister> registers, List<Long> cashIds) {
        Map<Long, CashAccumulator> values = new HashMap<>();
        registers.forEach(cash -> values.put(cash.getId(), new CashAccumulator(cash.getOpeningAmount())));

        paymentRepository.sumByCashRegisters(cashIds).forEach(row -> {
            if (row[1] != null) values.get((Long) row[0])
                    .addPos((PaymentMethod) row[1], (BigDecimal) row[2]);
        });
        eventPaymentRepository.sumByCashRegisters(cashIds).forEach(row -> values.get((Long) row[0])
                .addEvent((PaymentMethod) row[1], (BigDecimal) row[2]));
        Map<Long, Long> movementCounts = new HashMap<>();
        cashMovementRepository.sumByCashRegisters(cashIds).forEach(row -> {
            Long cashId = (Long) row[0];
            values.get(cashId).addMovement((CashMovementType) row[1], (BigDecimal) row[2]);
            movementCounts.merge(cashId, (Long) row[3], Long::sum);
        });

        Map<Long, CashSummary> result = new HashMap<>();
        values.forEach((id, value) -> result.put(id, value.toSummary()));
        return new CashHistoryBatch(result, movementCounts);
    }

    private static final class CashAccumulator {
        private final BigDecimal opening;
        private BigDecimal posCash = BigDecimal.ZERO;
        private BigDecimal posCard = BigDecimal.ZERO;
        private BigDecimal posTransfer = BigDecimal.ZERO;
        private BigDecimal eventCash = BigDecimal.ZERO;
        private BigDecimal eventCard = BigDecimal.ZERO;
        private BigDecimal eventTransfer = BigDecimal.ZERO;
        private BigDecimal deposits = BigDecimal.ZERO;
        private BigDecimal withdrawals = BigDecimal.ZERO;

        private CashAccumulator(BigDecimal opening) { this.opening = opening; }
        private void addPos(PaymentMethod method, BigDecimal amount) {
            switch (method) { case CASH -> posCash = amount; case CARD -> posCard = amount; case TRANSFER -> posTransfer = amount; }
        }
        private void addEvent(PaymentMethod method, BigDecimal amount) {
            switch (method) { case CASH -> eventCash = amount; case CARD -> eventCard = amount; case TRANSFER -> eventTransfer = amount; }
        }
        private void addMovement(CashMovementType type, BigDecimal amount) {
            if (type == CashMovementType.DEPOSIT) deposits = amount; else withdrawals = amount;
        }
        private CashSummary toSummary() {
            BigDecimal cash = posCash.add(eventCash);
            BigDecimal card = posCard.add(eventCard);
            BigDecimal transfer = posTransfer.add(eventTransfer);
            return new CashSummary(opening, posCash, eventCash, cash, posCard, eventCard, card,
                    posTransfer, eventTransfer, transfer, deposits, withdrawals,
                    opening.add(cash).add(deposits).subtract(withdrawals));
        }
    }

    private CashRegisterDetailResponse mapToCashRegisterDetailResponse(CashRegister cash) {
        CashSummary summary = calculateCashSummary(cash);
        BigDecimal totalSales = summary.cashSales()
                .add(summary.cardSales())
                .add(summary.transferSales());
        long movementCount = cashMovementRepository.countByCashRegister_Id(cash.getId());
        long orderCount = countOrders(cash);
        BigDecimal historicalExpectedCash = cash.getStatus() == CashStatus.CLOSED
                && cash.getExpectedAmount() != null ? cash.getExpectedAmount() : summary.expectedCash();

        return CashRegisterDetailResponse.builder()
                .publicId(cash.getPublicId())
                .status(cash.getStatus().name())
                .openingAmount(cash.getOpeningAmount())
                .cashSales(summary.cashSales())
                .cardSales(summary.cardSales())
                .transferSales(summary.transferSales())
                .posCashSales(summary.posCashSales())
                .eventCashPayments(summary.eventCashPayments())
                .posCardSales(summary.posCardSales())
                .eventCardPayments(summary.eventCardPayments())
                .posTransferSales(summary.posTransferSales())
                .eventTransferPayments(summary.eventTransferPayments())
                .salesTotal(totalSales)
                .depositTotal(summary.depositTotal())
                .withdrawalTotal(summary.withdrawalTotal())
                .expectedCash(historicalExpectedCash)
                .countedCash(cash.getClosingAmount())
                .difference(cash.getDifference())
                .openedAt(cash.getOpenedAt())
                .closedAt(cash.getClosedAt())
                .openedByName(cash.getOpenedBy().getName())
                .openedByPublicId(cash.getOpenedBy().getPublicId())
                .openedByEmail(cash.getOpenedBy().getEmail())
                .closedByName(cash.getClosedBy() != null
                        ? cash.getClosedBy().getName() : null)
                .closedByPublicId(cash.getClosedBy() != null ? cash.getClosedBy().getPublicId() : null)
                .closedByEmail(cash.getClosedBy() != null ? cash.getClosedBy().getEmail() : null)
                .branchPublicId(cash.getBranch().getPublicId())
                .branchName(cash.getBranch().getName())
                .orderCount(orderCount)
                .movementCount((int) movementCount)
                .build();
    }


    private CashSummary calculateCashSummary(CashRegister cash) {
        LocalDateTime start = cash.getOpenedAt();
        LocalDateTime end = cash.getClosedAt() != null
                ? cash.getClosedAt()
                : LocalDateTime.now();
        Long branchId = cash.getBranch().getId();

        BigDecimal posCashSales = safe(paymentRepository.sumCashPayments(branchId, start, end));
        BigDecimal posCardSales = safe(paymentRepository.sumCardPayments(branchId, start, end));
        BigDecimal posTransferSales = safe(paymentRepository.sumTransferPayments(branchId, start, end));

        BigDecimal eventCashPayments = safe(eventPaymentRepository.sumByCashRegisterAndPaymentMethod(
                cash.getId(), PaymentMethod.CASH));
        BigDecimal eventCardPayments = safe(eventPaymentRepository.sumByCashRegisterAndPaymentMethod(
                cash.getId(), PaymentMethod.CARD));
        BigDecimal eventTransferPayments = safe(eventPaymentRepository.sumByCashRegisterAndPaymentMethod(
                cash.getId(), PaymentMethod.TRANSFER));

        BigDecimal cashSales = posCashSales.add(eventCashPayments);
        BigDecimal cardSales = posCardSales.add(eventCardPayments);
        BigDecimal transferSales = posTransferSales.add(eventTransferPayments);

        BigDecimal depositTotal = safe(cashMovementRepository.sumByCashRegisterAndType(
                cash.getId(), CashMovementType.DEPOSIT));
        BigDecimal withdrawalTotal = safe(cashMovementRepository.sumByCashRegisterAndType(
                cash.getId(), CashMovementType.WITHDRAWAL));

        BigDecimal expectedCash = cash.getOpeningAmount()
                .add(cashSales)
                .add(depositTotal)
                .subtract(withdrawalTotal);

        return new CashSummary(
                cash.getOpeningAmount(),
                posCashSales,
                eventCashPayments,
                cashSales,
                posCardSales,
                eventCardPayments,
                cardSales,
                posTransferSales,
                eventTransferPayments,
                transferSales,
                depositTotal,
                withdrawalTotal,
                expectedCash);
    }

    private CashRegister getOpenCashRegister() {
        Long branchId = TenantContext.getBranchId();
        return cashRegisterRepository
                .findByBranch_IdAndStatus(branchId, CashStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("No hay caja abierta"));
    }

    private CashRegister getOpenCashRegisterForUpdate() {
        Long branchId = TenantContext.getBranchId();
        return cashRegisterRepository
                .findByBranch_IdAndStatusForUpdate(branchId, CashStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("No hay caja abierta"));
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private CashRegisterResponse buildResponse(CashRegister cash, CashSummary summary) {
        BigDecimal totalSales = summary.cashSales()
                .add(summary.cardSales())
                .add(summary.transferSales());

        CashRegisterResponse response = new CashRegisterResponse();
        response.setPublicId(cash.getPublicId());
        response.setOpeningAmount(cash.getOpeningAmount());

        response.setCashSales(summary.cashSales());
        response.setCardSales(summary.cardSales());
        response.setTransferSales(summary.transferSales());
        response.setPosCashSales(summary.posCashSales());
        response.setEventCashPayments(summary.eventCashPayments());
        response.setPosCardSales(summary.posCardSales());
        response.setEventCardPayments(summary.eventCardPayments());
        response.setPosTransferSales(summary.posTransferSales());
        response.setEventTransferPayments(summary.eventTransferPayments());

        response.setSalesTotal(totalSales);
        response.setExpectedCash(summary.expectedCash());
        response.setExpectedAmount(cash.getOpeningAmount().add(totalSales));

        response.setDepositTotal(summary.depositTotal());
        response.setWithdrawalTotal(summary.withdrawalTotal());

        response.setCountedAmount(cash.getClosingAmount());
        response.setDifference(cash.getDifference());

        response.setOpenedAt(cash.getOpenedAt());
        response.setClosedAt(cash.getClosedAt());
        response.setStatus(cash.getStatus().name());

        return response;
    }

    private CashMovementResponse mapMovementToResponse(CashMovement movement) {
        return CashMovementResponse.builder()
                .publicId(movement.getPublicId())
                .type(movement.getType().name())
                .amount(movement.getAmount())
                .reason(movement.getReason())
                .notes(movement.getNotes())
                .userName(movement.getUser().getName())
                .userPublicId(movement.getUser().getPublicId())
                .userEmail(movement.getUser().getEmail())
                .cashRegisterPublicId(movement.getCashRegister().getPublicId())
                .branchPublicId(movement.getBranch().getPublicId())
                .branchName(movement.getBranch().getName())
                .createdAt(movement.getCreatedAt())
                .voided(movement.getVoided())
                .voidedAt(movement.getVoidedAt())
                .voidedByName(movement.getVoidedBy() != null
                        ? movement.getVoidedBy().getName() : null)
                .voidReason(movement.getVoidReason())
                .build();
    }

    private long countOrders(CashRegister cash) {
        LocalDateTime end = cash.getClosedAt() != null ? cash.getClosedAt() : LocalDateTime.now();
        return orderRepository.countByCashPeriod(
                cash.getTenant().getId(), cash.getBranch().getId(), cash.getOpenedAt(), end);
    }

    private Pageable historyPage(int page, int size, String property) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc(property), Sort.Order.desc("id")));
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
}
