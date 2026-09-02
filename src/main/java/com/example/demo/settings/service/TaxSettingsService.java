package com.example.demo.settings.service;

import com.example.demo.settings.dto.*;
import com.example.demo.settings.model.TaxSettings;
import com.example.demo.settings.repository.TaxSettingsRepository;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TaxSettingsService {

    private final TaxSettingsRepository repository;
    private final TenantRepository tenantRepository;

    private TaxSettings findOrCreate(Long tenantId) {
        return repository
                .findByTenant_Id(tenantId)
                .orElseGet(() -> {
                    Tenant tenant = tenantRepository.findById(tenantId)
                            .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

                    TaxSettings defaults = new TaxSettings();
                    defaults.setTenant(tenant);
                    defaults.setTaxEnabled(true);
                    defaults.setTaxRate(new BigDecimal("0.16"));

                    return repository.save(defaults);
                });
    }

    public TaxSettingsResponse getSettings(Long tenantId) {

        TaxSettings settings = findOrCreate(tenantId);

        TaxSettingsResponse response = new TaxSettingsResponse();
        response.setTaxEnabled(settings.getTaxEnabled());
        response.setTaxRate(settings.getTaxRate());

        return response;
    }

    public TaxSettingsResponse updateSettings(Long tenantId, TaxSettingsRequest request) {

        TaxSettings settings = findOrCreate(tenantId);

        if (request.getTaxEnabled() != null) {
            settings.setTaxEnabled(request.getTaxEnabled());
        }
        if (request.getTaxRate() != null) {
            settings.setTaxRate(request.getTaxRate());
        }

        repository.save(settings);

        return getSettings(tenantId);
    }
}