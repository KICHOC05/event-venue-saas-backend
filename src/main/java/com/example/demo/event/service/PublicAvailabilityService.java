package com.example.demo.event.service;

import com.example.demo.common.enums.EventStatus;
import com.example.demo.common.enums.TenantStatus;
import com.example.demo.event.dto.PublicAvailabilityCalendarResponse;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicAvailabilityService {

    private static final long MAX_RANGE_DAYS = 62;

    private static final List<EventStatus> OCCUPYING_STATUSES = List.of(
            EventStatus.PENDING_DEPOSIT,
            EventStatus.CONFIRMED,
            EventStatus.IN_PROGRESS
    );

    private final TenantRepository tenantRepository;
    private final EventBookingRepository eventBookingRepository;

    @Transactional(readOnly = true)
    public PublicAvailabilityCalendarResponse getCalendar(
            String tenantPublicId,
            LocalDate from,
            LocalDate to) {
        validateRequest(tenantPublicId, from, to);

        Tenant tenant = tenantRepository
                .findByPublicIdAndStatus(tenantPublicId.trim(), TenantStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Negocio no encontrado"));

        List<LocalDate> occupiedDates = eventBookingRepository.findOccupiedDates(
                tenant.getId(),
                from,
                to,
                OCCUPYING_STATUSES
        );

        return PublicAvailabilityCalendarResponse.builder()
                .from(from)
                .to(to)
                .occupiedDates(List.copyOf(occupiedDates))
                .build();
    }

    private void validateRequest(String tenantPublicId, LocalDate from, LocalDate to) {
        if (tenantPublicId == null || tenantPublicId.isBlank()) {
            throw new IllegalArgumentException("tenantPublicId es obligatorio");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("El rango de fechas es obligatorio");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("El rango máximo permitido es de 62 días");
        }
    }
}
