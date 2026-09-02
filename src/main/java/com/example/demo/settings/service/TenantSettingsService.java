package com.example.demo.settings.service;

import com.example.demo.common.enums.InventoryMode;
import com.example.demo.security.TenantContext;
import com.example.demo.settings.dto.CompanySettingsRequest;
import com.example.demo.settings.dto.CompanySettingsResponse;
import com.example.demo.settings.dto.TenantSettingsResponse;
import com.example.demo.settings.model.TenantSettings;
import com.example.demo.settings.repository.TenantSettingsRepository;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TenantSettingsService {

    private final TenantSettingsRepository repository;
    private final TenantRepository tenantRepository;
    private final CloudinaryStorageService cloudinaryStorageService;

    // =========================
    // GET SETTINGS (entity)
    // =========================

    public TenantSettings getSettings() {

        Long tenantId = TenantContext.getTenantId();

        return repository
                .findByTenant_Id(tenantId)
                .orElseGet(() -> {
                    Tenant tenant = tenantRepository.findById(tenantId)
                            .orElseThrow();

                    TenantSettings settings = new TenantSettings();
                    settings.setTenant(tenant);
                    return repository.save(settings);
                });
    }

    // =========================
    // GET SETTINGS (DTO)
    // =========================

    public TenantSettingsResponse getSettingsResponse() {

        TenantSettings settings = getSettings();

        TenantSettingsResponse response = new TenantSettingsResponse();
        response.setInventoryMode(settings.getInventoryMode());

        return response;
    }

    // =========================
    // UPDATE INVENTORY MODE
    // =========================

    public InventoryMode updateInventoryMode(InventoryMode mode) {

        TenantSettings settings = getSettings();
        settings.setInventoryMode(mode);
        repository.save(settings);

        return settings.getInventoryMode();
    }

    // =========================
    // GET COMPANY SETTINGS
    // =========================

    public CompanySettingsResponse getCompanySettings() {

        Long tenantId = TenantContext.getTenantId();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

        CompanySettingsResponse response = new CompanySettingsResponse();
        response.setBusinessName(tenant.getBusinessName());
        response.setPhone(tenant.getPhone());
        response.setWebsite(tenant.getWebsite());
        response.setLogoUrl(tenant.getLogoUrl());

        return response;
    }

    // =========================
    // UPDATE COMPANY SETTINGS
    // =========================

    public CompanySettingsResponse updateCompanySettings(CompanySettingsRequest request) {

        Long tenantId = TenantContext.getTenantId();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

        if (request.getBusinessName() != null) {
            tenant.setBusinessName(request.getBusinessName());
        }
        if (request.getPhone() != null) {
            tenant.setPhone(request.getPhone());
        }
        if (request.getWebsite() != null) {
            tenant.setWebsite(request.getWebsite());
        }

        tenantRepository.save(tenant);

        return getCompanySettings();
    }

    // =========================
    // UPLOAD LOGO ← ACTUALIZADO
    // =========================

    public String uploadLogo(MultipartFile file) {

        Long tenantId = TenantContext.getTenantId();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

        // 1. Guardar referencia al logo anterior
        String oldLogoUrl = tenant.getLogoUrl();

        // 2. Subir nuevo logo a Cloudinary
        String newLogoUrl = cloudinaryStorageService.uploadTenantLogo(file, tenantId);

        // 3. Actualizar en base de datos
        tenant.setLogoUrl(newLogoUrl);
        tenantRepository.save(tenant);

        // 4. Borrar logo anterior de Cloudinary
        cloudinaryStorageService.deleteIfExists(oldLogoUrl);

        return newLogoUrl;
    }
}