package com.example.demo.order.repository;

import com.example.demo.common.enums.OrderItemStatus;
import com.example.demo.order.model.OrderItem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrder_Id(Long orderId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product WHERE oi.order.id IN :orderIds")
    List<OrderItem> findAllByOrderIdsWithProduct(@Param("orderIds") List<Long> orderIds);

    Optional<OrderItem> findByPublicId(String publicId);

    Optional<OrderItem> findByPublicIdAndOrder_PublicId(String publicId, String orderPublicId);

    @Query("""
                SELECT oi.product.publicId, oi.product.name,
                       SUM(oi.quantity), SUM(oi.subtotal)
                FROM OrderItem oi
                WHERE oi.order.tenant.id = :tenantId
                  AND oi.order.status = com.example.demo.common.enums.OrderStatus.CLOSED
                  AND oi.status = 'ACTIVE'
                  AND oi.order.createdAt BETWEEN :start AND :end
                  AND oi.product.type = com.example.demo.common.enums.ProductType.PRODUCT
                GROUP BY oi.product.publicId, oi.product.name
                ORDER BY SUM(oi.quantity) DESC
            """)
    List<Object[]> topProductsByTenant(
            @Param("tenantId") Long tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
                SELECT oi.product.publicId, oi.product.name,
                       SUM(oi.quantity), SUM(oi.subtotal)
                FROM OrderItem oi
                WHERE oi.order.tenant.id = :tenantId
                  AND oi.order.status = com.example.demo.common.enums.OrderStatus.CLOSED
                  AND oi.status = 'ACTIVE'
                  AND oi.order.createdAt BETWEEN :start AND :end
                  AND oi.product.type = com.example.demo.common.enums.ProductType.PACKAGE
                GROUP BY oi.product.publicId, oi.product.name
                ORDER BY SUM(oi.quantity) DESC
            """)
    List<Object[]> topPackagesByTenant(
            @Param("tenantId") Long tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
                SELECT oi.product.publicId, oi.product.name,
                       SUM(oi.quantity), SUM(oi.subtotal)
                FROM OrderItem oi
                WHERE oi.order.tenant.id = :tenantId
                  AND oi.order.status = com.example.demo.common.enums.OrderStatus.CLOSED
                  AND oi.status = 'ACTIVE'
                  AND oi.order.createdAt BETWEEN :start AND :end
                GROUP BY oi.product.publicId, oi.product.name
                ORDER BY SUM(oi.quantity) DESC
            """)
    List<Object[]> allItemsSoldByTenant(
            @Param("tenantId") Long tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
        SELECT oi.product.type,
               SUM(oi.quantity), SUM(oi.subtotal)
        FROM OrderItem oi
        WHERE oi.order.tenant.id = :tenantId
          AND oi.order.status = com.example.demo.common.enums.OrderStatus.CLOSED
          AND oi.status = 'ACTIVE'
          AND oi.order.createdAt BETWEEN :start AND :end
        GROUP BY oi.product.type
        ORDER BY SUM(oi.quantity) DESC
    """)
    List<Object[]> salesByProductType(
            @Param("tenantId") Long tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
    List<OrderItem> findByActiveTrueAndSessionEndBeforeAndOrder_Tenant_Id(
            LocalDateTime now,
            Long tenantId);

    @Query("""
        SELECT oi
        FROM OrderItem oi
        WHERE oi.order.tenant.id = :tenantId
        AND oi.active = true
        AND oi.product.type = com.example.demo.common.enums.ProductType.SERVICE
        AND oi.sessionStart IS NOT NULL
        AND oi.sessionEnd IS NOT NULL
        AND oi.durationMinutes IS NOT NULL
        ORDER BY oi.sessionEnd ASC
        """)
    List<OrderItem> findActiveTimers(
            @Param("tenantId") Long tenantId);

    @Query("""
        SELECT oi
        FROM OrderItem oi
        WHERE oi.active = true
        AND oi.product.type = com.example.demo.common.enums.ProductType.SERVICE
        AND oi.sessionStart IS NOT NULL
        AND oi.sessionEnd IS NOT NULL
        AND oi.durationMinutes IS NOT NULL
        ORDER BY oi.sessionEnd ASC
        """)
    List<OrderItem> findAllActiveTimers();

    List<OrderItem> findByEventDateAndOrder_Tenant_Id(
            LocalDate date,
            Long tenantId);

    List<OrderItem> findByActiveTrueAndOrder_Tenant_Id(Long tenantId);

    List<OrderItem> findByStatusAndOrder_Tenant_Id(
            OrderItemStatus status,
            Long tenantId);

    List<OrderItem> findByStatusAndOrder_Tenant_IdAndSessionStartBetween(
            OrderItemStatus status,
            Long tenantId,
            LocalDateTime start,
            LocalDateTime end);

    Long countByActiveTrueAndOrder_Tenant_Id(Long tenantId);

    Long countByStatusAndOrder_Tenant_Id(
            OrderItemStatus status,
            Long tenantId);

    @Query("""
        SELECT oi
        FROM OrderItem oi
        WHERE oi.order.tenant.id = :tenantId
        AND oi.product.type = com.example.demo.common.enums.ProductType.SERVICE
        AND oi.sessionStart IS NOT NULL
        AND oi.sessionEnd IS NOT NULL
        AND oi.durationMinutes IS NOT NULL
        AND (:status IS NULL OR oi.status = :status)
        AND (
            :search IS NULL
            OR LOWER(COALESCE(oi.childName,'')) LIKE LOWER(CONCAT('%',:search,'%'))
            OR LOWER(COALESCE(oi.order.customerName,'')) LIKE LOWER(CONCAT('%',:search,'%'))
        )
        AND (
            :startDate IS NULL OR :endDate IS NULL
            OR oi.sessionStart >= :startDate AND oi.sessionStart < :endDate
        )
        ORDER BY oi.sessionStart DESC
        """)
    Page<OrderItem> findTimerHistory(
            @Param("tenantId") Long tenantId,
            @Param("status") OrderItemStatus status,
            @Param("search") String search,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
