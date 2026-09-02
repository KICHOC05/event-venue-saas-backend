package com.example.demo.order.repository;

import com.example.demo.common.enums.OrderStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.order.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

        Optional<Order> findByPublicIdAndTenant_Id(String publicId, Long tenantId);

        @Query("""
                            SELECT COUNT(o)
                            FROM Order o
                            WHERE o.tenant.id = :tenantId
                              AND o.status = com.example.demo.common.enums.OrderStatus.CLOSED
                              AND o.createdAt BETWEEN :start AND :end
                        """)
        Long countClosedOrdersByTenant(
                        @Param("tenantId") Long tenantId,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        @EntityGraph(attributePaths = {"branch", "user", "client"})
        @Query("""
                        SELECT o FROM Order o
                        WHERE o.tenant.id = :tenantId
                          AND (:branchPublicId IS NULL OR o.branch.publicId = :branchPublicId)
                          AND (:userPublicId IS NULL OR o.user.publicId = :userPublicId)
                          AND (:orderNumber IS NULL OR o.id = :orderNumber)
                          AND (:paymentMethod IS NULL OR EXISTS (
                              SELECT p.id FROM Payment p
                              WHERE p.order = o AND p.paymentMethod = :paymentMethod
                          ))
                          AND (:status IS NULL OR o.status = :status)
                          AND (:createdAtFrom IS NULL OR o.createdAt >= :createdAtFrom)
                          AND (:createdAtTo IS NULL OR o.createdAt < :createdAtTo)
                          AND (:search IS NULL
                               OR LOWER(COALESCE(o.customerName,'')) LIKE LOWER(CONCAT('%',:search,'%'))
                               OR LOWER(COALESCE(o.user.name,'')) LIKE LOWER(CONCAT('%',:search,'%')))
                    """)
        Page<Order> findHistoryByTenant(
                        @Param("tenantId") Long tenantId,
                        @Param("branchPublicId") String branchPublicId,
                        @Param("userPublicId") String userPublicId,
                        @Param("orderNumber") Long orderNumber,
                        @Param("paymentMethod") PaymentMethod paymentMethod,
                        @Param("status") OrderStatus status,
                        @Param("createdAtFrom") LocalDateTime createdAtFrom,
                        @Param("createdAtTo") LocalDateTime createdAtTo,
                        @Param("search") String search,
                        Pageable pageable);

        @Query("""
                SELECT COUNT(o) FROM Order o
                WHERE o.tenant.id = :tenantId
                  AND o.branch.id = :branchId
                  AND o.createdAt >= :from
                  AND o.createdAt <= :to
                """)
        long countByCashPeriod(
                        @Param("tenantId") Long tenantId,
                        @Param("branchId") Long branchId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        @Query("""
                SELECT c.id, COUNT(o)
                FROM CashRegister c
                LEFT JOIN Order o ON o.tenant.id = c.tenant.id
                  AND o.branch.id = c.branch.id
                  AND o.createdAt >= c.openedAt
                  AND o.createdAt <= COALESCE(c.closedAt, CURRENT_TIMESTAMP)
                WHERE c.id IN :cashRegisterIds
                GROUP BY c.id
                """)
        java.util.List<Object[]> countByCashRegisters(
                        @Param("cashRegisterIds") java.util.List<Long> cashRegisterIds);
}
