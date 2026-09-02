package com.example.demo.cash.repository;

import com.example.demo.cash.model.CashRegister;
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
