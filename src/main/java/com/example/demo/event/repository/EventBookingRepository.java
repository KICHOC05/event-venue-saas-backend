package com.example.demo.event.repository;

import com.example.demo.common.enums.EventStatus;
import com.example.demo.event.model.EventBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventBookingRepository extends JpaRepository<EventBooking, Long> {

    Optional<EventBooking> findByPublicId(String publicId);

    Optional<EventBooking> findByPublicIdAndTenant_Id(String publicId, Long tenantId);

    List<EventBooking> findByTenant_Id(Long tenantId);

    @Query("""
        SELECT event
        FROM EventBooking event
        ORDER BY event.tenant.id, event.branch.id, event.createdAt, event.id
    """)
    List<EventBooking> findAllForEventNumberMigration();

    List<EventBooking> findByTenant_IdAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
            Long tenantId,
            LocalDate from,
            LocalDate to
    );

    @Query("SELECT DISTINCT e.eventDate FROM EventBooking e " +
            "WHERE e.tenant.id = :tenantId " +
            "AND e.eventDate BETWEEN :from AND :to " +
            "AND e.status IN :statuses " +
            "ORDER BY e.eventDate")
    List<LocalDate> findOccupiedDates(
            @Param("tenantId") Long tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") List<EventStatus> statuses
    );

    // Buscar conflictos por horario
    @Query("SELECT e FROM EventBooking e " +
            "WHERE e.tenant.id = :tenantId " +
            "AND e.eventDate = :date " +
            "AND e.status IN :statuses " +
            "AND ((e.startTime < :endTime AND e.endTime > :startTime))")
    List<EventBooking> findConflicts(
            @Param("tenantId") Long tenantId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("statuses") List<EventStatus> statuses
    );

    // Buscar conflictos excluyendo un evento (para updates)
    @Query("SELECT e FROM EventBooking e " +
            "WHERE e.tenant.id = :tenantId " +
            "AND e.eventDate = :date " +
            "AND e.status IN :statuses " +
            "AND e.publicId != :excludePublicId " +
            "AND ((e.startTime < :endTime AND e.endTime > :startTime))")
    List<EventBooking> findConflictsExcluding(
            @Param("tenantId") Long tenantId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("statuses") List<EventStatus> statuses,
            @Param("excludePublicId") String excludePublicId
    );

    // ✅ NUEVO: Verificar si ya existe un evento en la misma fecha
    @Query("SELECT COUNT(e) > 0 FROM EventBooking e " +
            "WHERE e.tenant.id = :tenantId " +
            "AND e.eventDate = :date " +
            "AND e.status IN :statuses")
    boolean existsByDateAndStatuses(
            @Param("tenantId") Long tenantId,
            @Param("date") LocalDate date,
            @Param("statuses") List<EventStatus> statuses
    );

    // ✅ NUEVO: Verificar si ya existe un evento en la misma fecha (excluyendo uno)
    @Query("SELECT COUNT(e) > 0 FROM EventBooking e " +
            "WHERE e.tenant.id = :tenantId " +
            "AND e.eventDate = :date " +
            "AND e.status IN :statuses " +
            "AND e.publicId != :excludePublicId")
    boolean existsByDateAndStatusesExcluding(
            @Param("tenantId") Long tenantId,
            @Param("date") LocalDate date,
            @Param("statuses") List<EventStatus> statuses,
            @Param("excludePublicId") String excludePublicId
    );

    // =====================================================
    // NOTIFICATION QUERIES
    // =====================================================

    @Query("SELECT e FROM EventBooking e " +
            "WHERE e.tenant.id = :tenantId " +
            "AND e.eventDate = :date " +
            "AND e.status IN :statuses")
    List<EventBooking> findByTenantAndDateAndStatuses(
            @Param("tenantId") Long tenantId,
            @Param("date") LocalDate date,
            @Param("statuses") List<EventStatus> statuses
    );

    @Query("SELECT e FROM EventBooking e " +
            "WHERE e.tenant.id = :tenantId " +
            "AND e.eventDate BETWEEN :from AND :to " +
            "AND e.status IN :statuses " +
            "AND e.remainingAmount > 0")
    List<EventBooking> findUpcomingWithPendingBalance(
            @Param("tenantId") Long tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") List<EventStatus> statuses
    );

    @Query("""
        SELECT eb.packageProduct.publicId, eb.packageProduct.name,
               COUNT(eb), COALESCE(SUM(eb.eventPrice), 0)
        FROM EventBooking eb
        WHERE eb.tenant.id = :tenantId
          AND eb.createdAt BETWEEN :start AND :end
          AND eb.status <> com.example.demo.common.enums.EventStatus.CANCELLED
        GROUP BY eb.packageProduct.publicId, eb.packageProduct.name
        ORDER BY COUNT(eb) DESC
    """)
    List<Object[]> topEventPackagesByTenant(
            @Param("tenantId") Long tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
        SELECT COUNT(eb)
        FROM EventBooking eb
        WHERE eb.tenant.id = :tenantId
          AND eb.eventDate BETWEEN :from AND :to
          AND eb.status <> com.example.demo.common.enums.EventStatus.CANCELLED
    """)
    Long countScheduledByTenantAndEventDateBetween(
            @Param("tenantId") Long tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
