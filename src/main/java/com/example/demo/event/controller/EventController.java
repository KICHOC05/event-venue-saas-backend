package com.example.demo.event.controller;

import com.example.demo.event.dto.AvailabilityResponse;
import com.example.demo.event.dto.CreateEventRequest;
import com.example.demo.event.dto.EventCalendarResponse;
import com.example.demo.event.dto.EventPaymentResponse;
import com.example.demo.event.dto.EventRescheduleHistoryResponse;
import com.example.demo.event.dto.EventResponse;
import com.example.demo.event.dto.RegisterEventPaymentRequest;
import com.example.demo.event.dto.RescheduleEventRequest;
import com.example.demo.event.dto.UpdateEventRequest;
import com.example.demo.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

 import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public EventResponse createEvent(@Valid @RequestBody CreateEventRequest request) {
        return eventService.createEvent(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public List<EventResponse> getEvents() {
        return eventService.getEvents();
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public EventResponse getEvent(@PathVariable String publicId) {
        return eventService.getEvent(publicId);
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public EventResponse updateEvent(@PathVariable String publicId, 
                                      @Valid @RequestBody UpdateEventRequest request) {
        return eventService.updateEvent(publicId, request);
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void cancelEvent(@PathVariable String publicId) {
        eventService.cancelEvent(publicId);
    }

    @PostMapping("/{publicId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public EventResponse confirmEvent(@PathVariable String publicId) {
        return eventService.confirmEvent(publicId);
    }

    @PostMapping("/{publicId}/start")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public EventResponse startEvent(@PathVariable String publicId) {
        return eventService.startEvent(publicId);
    }

    @PostMapping("/{publicId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public EventResponse completeEvent(@PathVariable String publicId) {
        return eventService.completeEvent(publicId);
    }

    @PutMapping("/{publicId}/reschedule")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public EventResponse rescheduleEvent(@PathVariable String publicId,
                                          @Valid @RequestBody RescheduleEventRequest request) {
        return eventService.rescheduleEvent(publicId, request);
    }

    @GetMapping("/{publicId}/reschedule-history")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public List<EventRescheduleHistoryResponse> getRescheduleHistory(@PathVariable String publicId) {
        return eventService.getRescheduleHistory(publicId);
    }

    @PostMapping("/{publicId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public EventPaymentResponse registerPayment(@PathVariable String publicId,
                                                 @Valid @RequestBody RegisterEventPaymentRequest request) {
        return eventService.registerEventPayment(publicId, request);
    }

    @GetMapping("/{publicId}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public List<EventPaymentResponse> getEventPayments(@PathVariable String publicId) {
        return eventService.getEventPayments(publicId);
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public List<EventCalendarResponse> getCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return eventService.getCalendar(from, to);
    }

    @GetMapping("/availability")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public AvailabilityResponse checkAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime end,
            @RequestParam(required = false) String excludePublicId) {
        return eventService.checkAvailability(date, start, end, excludePublicId);
    }

    @GetMapping("/payment-audit")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> paymentAudit() {
        List<Map<String, Object>> events = eventService.auditEventPaymentConsistency();
        return Map.of(
                "inconsistentEvents", events.size(),
                "events", events
        );
    }
}