package com.example.demo.cash.repository;

import com.example.demo.cash.model.CashRegister;
import com.example.demo.cash.repository.projection.CashPaymentTotalsProjection;
import com.example.demo.common.enums.CashStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {

    Optional<CashRegister> findByPublicIdAndTenant_Id(String publicId, Long tenantId);

    Optional<CashRegister> findByPublicIdAndTenant_IdAndBranch_Id(
            String publicId, Long tenantId, Long branchId);

    Optional<CashRegister> findByBranch_IdAndStatus(Long branchId, CashStatus status);

    Optional<CashRegister> findByTenant_IdAndBranch_IdAndStatus(
            Long tenantId, Long branchId, CashStatus status);

    @Query(value = """
        SELECT
            COALESCE(SUM(CASE WHEN totals.payment_source = 'POS'
                AND totals.payment_method = 'CASH' THEN totals.amount ELSE 0 END), 0)
                AS posCashSales,
            COALESCE(SUM(CASE WHEN totals.payment_source = 'POS'
                AND totals.payment_method = 'CARD' THEN totals.amount ELSE 0 END), 0)
                AS posCardSales,
            COALESCE(SUM(CASE WHEN totals.payment_source = 'POS'
                AND totals.payment_method = 'TRANSFER' THEN totals.amount ELSE 0 END), 0)
                AS posTransferSales,
            COALESCE(SUM(CASE WHEN totals.payment_source = 'EVENT'
                AND totals.payment_method = 'CASH' THEN totals.amount ELSE 0 END), 0)
                AS eventCashPayments,
            COALESCE(SUM(CASE WHEN totals.payment_source = 'EVENT'
                AND totals.payment_method = 'CARD' THEN totals.amount ELSE 0 END), 0)
                AS eventCardPayments,
            COALESCE(SUM(CASE WHEN totals.payment_source = 'EVENT'
                AND totals.payment_method = 'TRANSFER' THEN totals.amount ELSE 0 END), 0)
                AS eventTransferPayments
        FROM (
            SELECT 'POS' AS payment_source, p.payment_method, p.amount
            FROM payments p
            WHERE p.tenant_id = :tenantId
              AND p.branch_id = :branchId
              AND p.created_at BETWEEN :start AND :end
            UNION ALL
            SELECT 'EVENT' AS payment_source, ep.payment_method, ep.amount
            FROM event_payments ep
            WHERE ep.tenant_id = :tenantId
              AND ep.branch_id = :branchId
              AND ep.cash_register_id = :cashRegisterId
        ) totals
        """, nativeQuery = true)
    CashPaymentTotalsProjection sumPaymentTotals(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("cashRegisterId") Long cashRegisterId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c FROM CashRegister c
        WHERE c.tenant.id = :tenantId
          AND c.branch.id = :branchId
          AND c.status = :status
    """)
    Optional<CashRegister> findByTenant_IdAndBranch_IdAndStatusForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("status") CashStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CashRegister c WHERE c.branch.id = :branchId AND c.status = :status")
    Optional<CashRegister> findByBranch_IdAndStatusForUpdate(
            @Param("branchId") Long branchId,
            @Param("status") CashStatus status);

    @Query("""
                SELECT c FROM CashRegister c
                WHERE c.tenant.id = :tenantId
                  AND (:branchPublicId IS NULL OR c.branch.publicId = :branchPublicId)
                  AND (:status IS NULL OR c.status = :status)
                  AND (:openedByPublicId IS NULL OR c.openedBy.publicId = :openedByPublicId)
                  AND (CAST(:from AS java.time.LocalDateTime) IS NULL OR c.openedAt >= :from)
                  AND (CAST(:toExclusive AS java.time.LocalDateTime) IS NULL OR c.openedAt < :toExclusive)
            """)
    Page<CashRegister> findHistoryByBranch(
            @Param("tenantId") Long tenantId,
            @Param("branchPublicId") String branchPublicId,
            @Param("status") CashStatus status,
            @Param("openedByPublicId") String openedByPublicId,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable);
}
