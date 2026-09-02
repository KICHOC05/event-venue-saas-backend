package com.example.demo.cash.repository;

import com.example.demo.cash.model.CashSettings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CashSettingsRepository extends JpaRepository<CashSettings, Long> {

    Optional<CashSettings> findByBranch_Id(Long branchId);
}
