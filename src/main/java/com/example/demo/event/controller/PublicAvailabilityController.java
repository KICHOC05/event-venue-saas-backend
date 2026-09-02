package com.example.demo.event.controller;

import com.example.demo.event.dto.PublicAvailabilityCalendarResponse;
import com.example.demo.event.service.PublicAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/public/availability")
@RequiredArgsConstructor
public class PublicAvailabilityController {

    private final PublicAvailabilityService publicAvailabilityService;

    @GetMapping("/calendar")
    public PublicAvailabilityCalendarResponse getCalendar(
            @RequestParam String tenantPublicId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return publicAvailabilityService.getCalendar(tenantPublicId, from, to);
    }
}
