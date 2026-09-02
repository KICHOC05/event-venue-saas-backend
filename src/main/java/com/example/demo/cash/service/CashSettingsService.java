package com.example.demo.cash.service;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.cash.dto.CashSettingsResponse;
import com.example.demo.cash.model.CashSettings;
import com.example.demo.cash.repository.CashSettingsRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CashSettingsService {

    private final CashSettingsRepository cashSettingsRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    public CashSettingsResponse getSettings(Long tenantId, Long branchId) {
        CashSettings settings = cashSettingsRepository.findByBranch_Id(branchId)
                .orElseGet(() -> createDefault(tenantId, branchId));
        return new CashSettingsResponse(settings.getDefaultOpeningAmount());
    }

    public CashSettingsResponse updateOpeningAmount(Long tenantId, Long branchId, Long userId, BigDecimal amount) {
        validateAmount(amount);

        CashSettings settings = cashSettingsRepository.findByBranch_Id(branchId)
                .orElseGet(() -> createDefault(tenantId, branchId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        settings.setDefaultOpeningAmount(amount);
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(user);

        cashSettingsRepository.save(settings);

        return new CashSettingsResponse(settings.getDefaultOpeningAmount());
    }

    public BigDecimal getDefaultOpeningAmount(Long branchId) {
        return cashSettingsRepository.findByBranch_Id(branchId)
                .map(CashSettings::getDefaultOpeningAmount)
                .orElse(BigDecimal.ZERO);
    }

    private CashSettings createDefault(Long tenantId, Long branchId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found"));

        CashSettings settings = new CashSettings();
        settings.setTenant(tenant);
        settings.setBranch(branch);
        settings.setDefaultOpeningAmount(BigDecimal.ZERO);
        settings.setCreatedAt(LocalDateTime.now());

        return cashSettingsRepository.save(settings);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("defaultOpeningAmount es obligatorio");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("defaultOpeningAmount no puede ser negativo");
        }
    }
}
