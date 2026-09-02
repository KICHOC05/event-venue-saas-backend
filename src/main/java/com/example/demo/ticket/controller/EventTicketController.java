package com.example.demo.ticket.controller;

import com.example.demo.ticket.service.TicketService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventTicketController {

    private final TicketService ticketService;

    @GetMapping("/{eventPublicId}/ticket")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public String getEventTicket(@PathVariable String eventPublicId) {
        return ticketService.generateEventTicket(eventPublicId);
    }

    @GetMapping(
            value = "/{eventPublicId}/payments/{paymentPublicId}/receipt",
            produces = MediaType.TEXT_HTML_VALUE
    )
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public String getEventPaymentReceipt(@PathVariable String eventPublicId,
                                         @PathVariable String paymentPublicId) {
        return ticketService.generateEventPaymentReceipt(eventPublicId, paymentPublicId);
    }
}
