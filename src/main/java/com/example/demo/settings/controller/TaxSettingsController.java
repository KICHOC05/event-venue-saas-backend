package com.example.demo.settings.controller;

import com.example.demo.security.TenantContext;
import com.example.demo.settings.dto.TaxSettingsRequest;
import com.example.demo.settings.dto.TaxSettingsResponse;
import com.example.demo.settings.service.TaxSettingsService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/tax")
@RequiredArgsConstructor
public class TaxSettingsController {

    private final TaxSettingsService taxSettingsService;

    // =========================
    // GET TAX SETTINGS
    // =========================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public TaxSettingsResponse getSettings() {

        Long tenantId = TenantContext.getTenantId();

        return taxSettingsService.getSettings(tenantId);
    }

    // =========================
    // UPDATE TAX SETTINGS
    // =========================

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public TaxSettingsResponse updateSettings(
            @RequestBody TaxSettingsRequest request) {

        Long tenantId = TenantContext.getTenantId();

        return taxSettingsService.updateSettings(tenantId, request);
    }
}