package com.example.demo.audit.controller;

import com.example.demo.audit.dto.FinancialAuditEntryResponse;
import com.example.demo.audit.dto.FinancialAuditSource;
import com.example.demo.audit.service.FinancialAuditService;
import com.example.demo.common.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/pos/audit")
@RequiredArgsConstructor
public class FinancialAuditController {

    private final FinancialAuditService financialAuditService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Page<FinancialAuditEntryResponse> getAudit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) FinancialAuditSource source,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) String userPublicId,
            @RequestParam(required = false) String branchPublicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return financialAuditService.getAudit(page, size, source, paymentMethod,
                userPublicId, branchPublicId, from, to);
    }
}
