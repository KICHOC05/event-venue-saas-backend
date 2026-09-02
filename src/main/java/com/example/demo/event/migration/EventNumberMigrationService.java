package com.example.demo.event.migration;

import com.example.demo.document.model.DocumentType;
import com.example.demo.document.service.DocumentSequenceService;
import com.example.demo.event.model.EventBooking;
import com.example.demo.event.repository.EventBookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventNumberMigrationService {

    private final EventBookingRepository eventBookingRepository;
    private final DocumentSequenceService documentSequenceService;

    @Transactional
    public MigrationResult migrateHistoricalEvents() {
        List<EventBooking> orderedEvents = eventBookingRepository.findAllForEventNumberMigration();
        Map<SequenceScope, List<EventBooking>> eventsByScope = new LinkedHashMap<>();

        for (EventBooking event : orderedEvents) {
            SequenceScope scope = new SequenceScope(
                    event.getTenant().getId(),
                    event.getBranch().getId()
            );
            eventsByScope.computeIfAbsent(scope, ignored -> new java.util.ArrayList<>())
                    .add(event);
        }

        int migratedEvents = 0;
        for (List<EventBooking> scopedEvents : eventsByScope.values()) {
            EventBooking first = scopedEvents.getFirst();
            long highestExistingNumber = scopedEvents.stream()
                    .map(EventBooking::getEventNumber)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L);

            documentSequenceService.ensureCurrentValueAtLeast(
                    first.getTenant(),
                    first.getBranch(),
                    DocumentType.EVENT,
                    highestExistingNumber
            );

            for (EventBooking event : scopedEvents) {
                if (event.getEventNumber() != null) {
                    continue;
                }
                event.setEventNumber(documentSequenceService.nextNumber(
                        event.getTenant(),
                        event.getBranch(),
                        DocumentType.EVENT
                ));
                migratedEvents++;
            }
        }

        eventBookingRepository.saveAll(orderedEvents);
        log.info(
                "Migración de numeración de eventos completada: {} eventos, {} secuencias",
                migratedEvents,
                eventsByScope.size()
        );
        return new MigrationResult(migratedEvents, eventsByScope.size());
    }

    private record SequenceScope(Long tenantId, Long branchId) {
    }

    public record MigrationResult(int migratedEvents, int sequenceScopes) {
    }
}
