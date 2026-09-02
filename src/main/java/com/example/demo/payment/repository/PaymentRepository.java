package com.example.demo.payment.repository;

import com.example.demo.payment.model.Payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByOrder_Id(Long orderId);

    @EntityGraph(attributePaths = {"user", "branch"})
    List<Payment> findAllByOrder_IdOrderByCreatedAtAscIdAsc(Long orderId);

    @EntityGraph(attributePaths = {"user", "branch", "order"})
    @Query("SELECT p FROM Payment p WHERE p.order.id IN :orderIds ORDER BY p.createdAt ASC, p.id ASC")
    List<Payment> findAllByOrderIds(@Param("orderIds") List<Long> orderIds);

    @EntityGraph(attributePaths = {"order", "user", "branch"})
    @Query("""
        SELECT p FROM Payment p
        WHERE p.tenant.id = :tenantId
          AND (:branchPublicId IS NULL OR p.branch.publicId = :branchPublicId)
          AND (:paymentMethod IS NULL OR p.paymentMethod = :paymentMethod)
          AND (:userPublicId IS NULL OR p.user.publicId = :userPublicId)
          AND (:from IS NULL OR p.createdAt >= :from)
          AND (:toExclusive IS NULL OR p.createdAt < :toExclusive)
        ORDER BY p.createdAt DESC, p.id DESC
    """)
    Page<Payment> findForAudit(
            @Param("tenantId") Long tenantId,
            @Param("branchPublicId") String branchPublicId,
            @Param("paymentMethod") com.example.demo.common.enums.PaymentMethod paymentMethod,
            @Param("userPublicId") String userPublicId,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable);

    @Query("""
        SELECT c.id, p.paymentMethod, COALESCE(SUM(p.amount), 0)
        FROM CashRegister c
        LEFT JOIN Payment p ON p.branch.id = c.branch.id
          AND p.tenant.id = c.tenant.id
          AND p.createdAt >= c.openedAt
          AND p.createdAt <= COALESCE(c.closedAt, CURRENT_TIMESTAMP)
        WHERE c.id IN :cashRegisterIds
        GROUP BY c.id, p.paymentMethod
    """)
    List<Object[]> sumByCashRegisters(@Param("cashRegisterIds") List<Long> cashRegisterIds);

    @Query("""
                SELECT COALESCE(SUM(p.amount), 0)
                FROM Payment p
                WHERE p.order.id = :orderId
            """)
    BigDecimal sumPaymentsByOrderId(@Param("orderId") Long orderId);

    @Query("""
                SELECT COALESCE(SUM(p.amount), 0)
                FROM Payment p
                WHERE p.branch.id = :branchId
                  AND p.createdAt BETWEEN :start AND :end
            """)
    BigDecimal sumTotalPayments(
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
                SELECT COALESCE(SUM(p.amount), 0)
                FROM Payment p
                WHERE p.tenant.id = :tenantId
                  AND p.createdAt BETWEEN :start AND :end
            """)
    BigDecimal sumTotalPaymentsByTenant(
            @Param("tenantId") Long tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
                SELECT COALESCE(SUM(p.amount), 0)
                FROM Payment p
                WHERE p.tenant.id = :tenantId
                  AND p.createdAt >= :startInclusive
                  AND p.createdAt < :endExclusive
            """)
    BigDecimal sumTotalPaymentsByTenantInPeriod(
            @Param("tenantId") Long tenantId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query("""
                SELECT COALESCE(SUM(p.amount), 0)
                FROM Payment p
                WHERE p.branch.id = :branchId
                  AND p.paymentMethod = com.example.demo.common.enums.PaymentMethod.CASH
                  AND p.createdAt BETWEEN :start AND :end
            """)
    BigDecimal sumCashPayments(
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
                SELECT COALESCE(SUM(p.amount), 0)
                FROM Payment p
                WHERE p.branch.id = :branchId
                  AND p.paymentMethod = com.example.demo.common.enums.PaymentMethod.CARD
                  AND p.createdAt BETWEEN :start AND :end
            """)
    BigDecimal sumCardPayments(
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
                SELECT COALESCE(SUM(p.amount), 0)
                FROM Payment p
                WHERE p.branch.id = :branchId
                  AND p.paymentMethod = com.example.demo.common.enums.PaymentMethod.TRANSFER
                  AND p.createdAt BETWEEN :start AND :end
            """)
    BigDecimal sumTransferPayments(
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = """
                SELECT DATE(p.created_at) as sale_date,
                       COALESCE(SUM(p.amount), 0) as total
                FROM payments p
                WHERE p.tenant_id = :tenantId
                  AND p.created_at BETWEEN :start AND :end
                GROUP BY DATE(p.created_at)
                ORDER BY DATE(p.created_at)
            """, nativeQuery = true)
    List<Object[]> dailySalesByTenant(
            @Param("tenantId") Long tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = """
                SELECT DATE(p.created_at) as sale_date,
                       COALESCE(SUM(p.amount), 0) as total
                FROM payments p
                WHERE p.tenant_id = :tenantId
                  AND p.created_at >= :startInclusive
                  AND p.created_at < :endExclusive
                GROUP BY DATE(p.created_at)
                ORDER BY DATE(p.created_at)
            """, nativeQuery = true)
    List<Object[]> dailySalesByTenantInPeriod(
            @Param("tenantId") Long tenantId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    // PaymentRepository.java - Cambiar el retorno de la query
    @Query(value = """
                SELECT
                    COALESCE(SUM(CASE WHEN p.payment_method = 'CASH' THEN p.amount ELSE 0 END), 0) as cash_total,
                    COALESCE(SUM(CASE WHEN p.payment_method = 'CARD' THEN p.amount ELSE 0 END), 0) as card_total,
                    COALESCE(SUM(CASE WHEN p.payment_method = 'TRANSFER' THEN p.amount ELSE 0 END), 0) as transfer_total
                FROM payments p
                WHERE p.tenant_id = :tenantId
                  AND p.created_at BETWEEN :start AND :end
            """, nativeQuery = true)
    List<Object[]> paymentBreakdownByTenant(
            @Param("tenantId") Long tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = """
                SELECT
                    COALESCE(SUM(CASE WHEN p.payment_method = 'CASH' THEN p.amount ELSE 0 END), 0) as cash_total,
                    COALESCE(SUM(CASE WHEN p.payment_method = 'CARD' THEN p.amount ELSE 0 END), 0) as card_total,
                    COALESCE(SUM(CASE WHEN p.payment_method = 'TRANSFER' THEN p.amount ELSE 0 END), 0) as transfer_total
                FROM payments p
                WHERE p.tenant_id = :tenantId
                  AND p.created_at >= :startInclusive
                  AND p.created_at < :endExclusive
            """, nativeQuery = true)
    List<Object[]> paymentBreakdownByTenantInPeriod(
            @Param("tenantId") Long tenantId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);
}
