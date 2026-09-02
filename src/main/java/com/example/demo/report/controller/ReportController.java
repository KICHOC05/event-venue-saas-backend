package com.example.demo.report.controller;

import com.example.demo.report.service.SalesReportService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final SalesReportService salesReportService;

    @GetMapping(value = "/sales-ticket", produces = MediaType.TEXT_HTML_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String getSalesTicket(@RequestParam(defaultValue = "WEEKLY") SalesReportService.ReportPeriod period) {
        return salesReportService.generateSalesReportTicket(period);
    }
}
