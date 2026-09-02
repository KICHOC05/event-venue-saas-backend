package com.example.demo.settings.controller;

import com.example.demo.settings.dto.CompanySettingsRequest;
import com.example.demo.settings.dto.CompanySettingsResponse;
import com.example.demo.settings.dto.InventoryModeRequest;
import com.example.demo.settings.dto.TenantSettingsResponse;
import com.example.demo.settings.service.TenantSettingsService;

import lombok.RequiredArgsConstructor;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class TenantSettingsController {

    private final TenantSettingsService service;

    // =========================
    // GET INVENTORY SETTINGS
    // =========================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public TenantSettingsResponse getSettings() {
        return service.getSettingsResponse();
    }

    // =========================
    // UPDATE INVENTORY MODE
    // =========================

    @PutMapping("/inventory-mode")
    @PreAuthorize("hasRole('ADMIN')")
    public TenantSettingsResponse updateInventoryMode(
            @RequestBody InventoryModeRequest request) {

        service.updateInventoryMode(request.getInventoryMode());
        return service.getSettingsResponse();
    }

    // =========================
    // GET COMPANY INFO
    // =========================

    @GetMapping("/company")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CompanySettingsResponse getCompany() {
        return service.getCompanySettings();
    }

    // =========================
    // UPDATE COMPANY INFO
    // =========================

    @PutMapping("/company")
    @PreAuthorize("hasRole('ADMIN')")
    public CompanySettingsResponse updateCompany(
            @RequestBody CompanySettingsRequest request) {

        return service.updateCompanySettings(request);
    }

    // =========================
    // UPLOAD LOGO
    // =========================

    @PostMapping("/logo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> uploadLogo(
            @RequestParam("logo") MultipartFile file) {

        String logoUrl = service.uploadLogo(file);

        return ResponseEntity.ok(Map.of("logoUrl", logoUrl));
    }
}