package com.example.demo.event.repository;

import com.example.demo.event.model.EventPayment;
import com.example.demo.common.enums.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventPaymentRepository extends JpaRepository<EventPayment, Long> {

    List<EventPayment> findByEventBooking_PublicIdAndTenant_IdOrderByPaidAtDesc(
            String eventPublicId, Long tenantId
    );

    Optional<EventPayment> findByPublicIdAndEventBooking_PublicIdAndTenant_Id(
            String paymentPublicId, String eventPublicId, Long tenantId
    );

    List<EventPayment> findByEventBooking_PublicIdAndTenant_IdOrderByPaidAtAscIdAsc(
            String eventPublicId, Long tenantId
    );

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM EventPayment p
        WHERE p.cashRegister.id = :cashRegisterId
          AND p.paymentMethod = :paymentMethod
    """)
    java.math.BigDecimal sumByCashRegisterAndPaymentMethod(
            @Param("cashRegisterId") Long cashRegisterId,
            @Param("paymentMethod") PaymentMethod paymentMethod
    );

    @EntityGraph(attributePaths = {"eventBooking", "branch", "cashRegister"})
    @Query("""
        SELECT p FROM EventPayment p
        WHERE p.tenant.id = :tenantId
          AND (:branchPublicId IS NULL OR p.branch.publicId = :branchPublicId)
          AND (:paymentMethod IS NULL OR p.paymentMethod = :paymentMethod)
          AND (:userPublicId IS NULL OR p.receivedByUserPublicId = :userPublicId)
          AND (:from IS NULL OR p.paidAt >= :from)
          AND (:toExclusive IS NULL OR p.paidAt < :toExclusive)
        ORDER BY p.paidAt DESC, p.id DESC
    """)
    Page<EventPayment> findForAudit(
            @Param("tenantId") Long tenantId,
            @Param("branchPublicId") String branchPublicId,
            @Param("paymentMethod") PaymentMethod paymentMethod,
            @Param("userPublicId") String userPublicId,
            @Param("from") java.time.LocalDateTime from,
            @Param("toExclusive") java.time.LocalDateTime toExclusive,
            Pageable pageable);

    @Query("""
        SELECT p.cashRegister.id, p.paymentMethod, COALESCE(SUM(p.amount), 0)
        FROM EventPayment p
        WHERE p.cashRegister.id IN :cashRegisterIds
        GROUP BY p.cashRegister.id, p.paymentMethod
    """)
    List<Object[]> sumByCashRegisters(@Param("cashRegisterIds") List<Long> cashRegisterIds);

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM EventPayment p
        WHERE p.tenant.id = :tenantId
          AND p.paidAt >= :startInclusive
          AND p.paidAt < :endExclusive
    """)
    BigDecimal sumTotalByTenantInPeriod(
            @Param("tenantId") Long tenantId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query(value = """
        SELECT DATE(ep.paid_at) AS sale_date,
               COALESCE(SUM(ep.amount), 0) AS total
        FROM event_payments ep
        WHERE ep.tenant_id = :tenantId
          AND ep.paid_at >= :startInclusive
          AND ep.paid_at < :endExclusive
        GROUP BY DATE(ep.paid_at)
        ORDER BY DATE(ep.paid_at)
    """, nativeQuery = true)
    List<Object[]> dailySalesByTenantInPeriod(
            @Param("tenantId") Long tenantId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query(value = """
        SELECT
            COALESCE(SUM(CASE WHEN ep.payment_method = 'CASH' THEN ep.amount ELSE 0 END), 0) AS cash_total,
            COALESCE(SUM(CASE WHEN ep.payment_method = 'CARD' THEN ep.amount ELSE 0 END), 0) AS card_total,
            COALESCE(SUM(CASE WHEN ep.payment_method = 'TRANSFER' THEN ep.amount ELSE 0 END), 0) AS transfer_total
        FROM event_payments ep
        WHERE ep.tenant_id = :tenantId
          AND ep.paid_at >= :startInclusive
          AND ep.paid_at < :endExclusive
    """, nativeQuery = true)
    List<Object[]> paymentBreakdownByTenantInPeriod(
            @Param("tenantId") Long tenantId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query("""
        SELECT COUNT(DISTINCT p.eventBooking.id)
        FROM EventPayment p
        WHERE p.tenant.id = :tenantId
          AND p.paidAt >= :startInclusive
          AND p.paidAt < :endExclusive
    """)
    Long countPaidEventsByTenantInPeriod(
            @Param("tenantId") Long tenantId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query("""
        SELECT p.eventBooking.packageProduct.publicId,
               p.eventBooking.packageProduct.name,
               COUNT(DISTINCT p.eventBooking.id),
               COALESCE(SUM(p.amount), 0)
        FROM EventPayment p
        WHERE p.tenant.id = :tenantId
          AND p.paidAt >= :startInclusive
          AND p.paidAt < :endExclusive
        GROUP BY p.eventBooking.packageProduct.publicId, p.eventBooking.packageProduct.name
        ORDER BY COUNT(DISTINCT p.eventBooking.id) DESC, SUM(p.amount) DESC
    """)
    List<Object[]> paidPackagesByTenantInPeriod(
            @Param("tenantId") Long tenantId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);
}
