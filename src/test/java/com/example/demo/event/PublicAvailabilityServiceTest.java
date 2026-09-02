package com.example.demo.event;

import com.example.demo.common.enums.EventStatus;
import com.example.demo.common.enums.TenantStatus;
import com.example.demo.event.dto.PublicAvailabilityCalendarResponse;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.event.service.PublicAvailabilityService;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicAvailabilityServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private EventBookingRepository eventBookingRepository;

    @InjectMocks private PublicAvailabilityService publicAvailabilityService;

    @Test
    void returnsOnlyDatesOccupiedByActiveWorkflowStatuses() {
        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setPublicId("tenant-public-id");
        tenant.setBusinessName("Space Kids");

        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        List<LocalDate> occupiedDates = List.of(
                LocalDate.of(2026, 8, 8),
                LocalDate.of(2026, 8, 22)
        );

        when(tenantRepository.findByPublicIdAndStatus("tenant-public-id", TenantStatus.ACTIVE))
                .thenReturn(Optional.of(tenant));
        when(eventBookingRepository.findOccupiedDates(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(from),
                org.mockito.ArgumentMatchers.eq(to),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(occupiedDates);

        PublicAvailabilityCalendarResponse response = publicAvailabilityService
                .getCalendar(" tenant-public-id ", from, to);

        assertEquals(from, response.getFrom());
        assertEquals(to, response.getTo());
        assertEquals(occupiedDates, response.getOccupiedDates());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EventStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventBookingRepository).findOccupiedDates(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(from),
                org.mockito.ArgumentMatchers.eq(to),
                statusesCaptor.capture());

        assertEquals(List.of(
                EventStatus.PENDING_DEPOSIT,
                EventStatus.CONFIRMED,
                EventStatus.IN_PROGRESS
        ), statusesCaptor.getValue());
    }

    @Test
    void rejectsRangesLongerThanSixtyTwoDays() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> publicAvailabilityService.getCalendar(
                        "tenant-public-id", from, from.plusDays(63)));

        assertEquals("El rango máximo permitido es de 62 días", error.getMessage());
    }

    @Test
    void rejectsUnknownOrInactiveTenant() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        when(tenantRepository.findByPublicIdAndStatus("unknown", TenantStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> publicAvailabilityService.getCalendar("unknown", from, from.plusDays(30)));
    }
}
