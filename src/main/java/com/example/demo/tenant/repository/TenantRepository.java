package com.example.demo.tenant.repository;

import com.example.demo.tenant.model.Tenant;
import com.example.demo.common.enums.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    // 🔎 Buscar por UUID público
    Optional<Tenant> findByPublicId(String publicId);

    // 🔎 Buscar solo si está activo
    Optional<Tenant> findByPublicIdAndStatus(String publicId, TenantStatus status);

    // 🔎 Listar por estado (para admin global)
    List<Tenant> findAllByStatus(TenantStatus status);

    // 🔎 Validar nombre único
    boolean existsByBusinessName(String businessName);
}