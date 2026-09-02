package com.example.demo.settings.repository;

import com.example.demo.settings.model.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {

    Optional<TenantSettings> findByTenant_Id(Long tenantId);

}