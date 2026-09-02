package com.example.demo.cash.repository;

import com.example.demo.cash.model.CashMovement;
import com.example.demo.common.enums.CashMovementType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    Optional<CashMovement> findByPublicIdAndTenant_IdAndBranch_Id(
            String publicId, Long tenantId, Long branchId);

    Optional<CashMovement> findByPublicIdAndTenant_Id(String publicId, Long tenantId);

    List<CashMovement> findByCashRegister_IdAndVoidedFalse(Long cashRegisterId);

    List<CashMovement> findByCashRegister_IdOrderByCreatedAtDesc(Long cashRegisterId);

    @Query("""
                SELECT COALESCE(SUM(cm.amount), 0)
                FROM CashMovement cm
                WHERE cm.cashRegister.id = :cashRegisterId
                  AND cm.type = :type
                  AND cm.voided = false
            """)
    BigDecimal sumByCashRegisterAndType(
            @Param("cashRegisterId") Long cashRegisterId,
            @Param("type") CashMovementType type);

    long countByCashRegister_Id(Long cashRegisterId);

    @Query("""
                SELECT cm FROM CashMovement cm
                WHERE cm.tenant.id = :tenantId
                  AND (:branchPublicId IS NULL OR cm.branch.publicId = :branchPublicId)
                  AND (:cashRegisterPublicId IS NULL OR cm.cashRegister.publicId = :cashRegisterPublicId)
                  AND (:type IS NULL OR cm.type = :type)
                  AND (:voided IS NULL OR cm.voided = :voided)
                  AND (:userPublicId IS NULL OR cm.user.publicId = :userPublicId)
                  AND (CAST(:from AS java.time.LocalDateTime) IS NULL OR cm.createdAt >= :from)
                  AND (CAST(:toExclusive AS java.time.LocalDateTime) IS NULL OR cm.createdAt < :toExclusive)
            """)
    Page<CashMovement> findHistoryByBranch(
            @Param("tenantId") Long tenantId,
            @Param("branchPublicId") String branchPublicId,
            @Param("cashRegisterPublicId") String cashRegisterPublicId,
            @Param("type") CashMovementType type,
            @Param("voided") Boolean voided,
            @Param("userPublicId") String userPublicId,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "branch", "cashRegister"})
    @Query("""
        SELECT cm FROM CashMovement cm
        WHERE cm.tenant.id = :tenantId
          AND (:branchPublicId IS NULL OR cm.branch.publicId = :branchPublicId)
          AND (:userPublicId IS NULL OR cm.user.publicId = :userPublicId)
          AND (:from IS NULL OR cm.createdAt >= :from)
          AND (:toExclusive IS NULL OR cm.createdAt < :toExclusive)
        ORDER BY cm.createdAt DESC, cm.id DESC
    """)
    Page<CashMovement> findForAudit(
            @Param("tenantId") Long tenantId,
            @Param("branchPublicId") String branchPublicId,
            @Param("userPublicId") String userPublicId,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable);

    @Query("""
        SELECT cm.cashRegister.id, cm.type, COALESCE(SUM(cm.amount), 0), COUNT(cm)
        FROM CashMovement cm
        WHERE cm.cashRegister.id IN :cashRegisterIds
          AND cm.voided = false
        GROUP BY cm.cashRegister.id, cm.type
    """)
    List<Object[]> sumByCashRegisters(@Param("cashRegisterIds") List<Long> cashRegisterIds);
}
