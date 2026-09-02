package com.example.demo.settings.repository;

import com.example.demo.settings.model.TaxSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaxSettingsRepository extends JpaRepository<TaxSettings, Long> {

    Optional<TaxSettings> findByTenant_Id(Long tenantId);
}