package com.example.demo.user.repository;

import com.example.demo.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    // 🔎 Buscar por UUID y tenant
    Optional<User> findByPublicIdAndTenant_Id(String publicId, Long tenantId);

    // 🔎 Buscar por email dentro del tenant
    Optional<User> findByEmailAndTenant_Id(String email, Long tenantId);

    // 🔎 Buscar solo por UUID (para JWT)
    Optional<User> findByPublicId(String publicId);

    // 🔎 Validar email único dentro del tenant
    boolean existsByTenant_IdAndEmail(Long tenantId, String email);

    // 🔎 Listar usuarios del tenant
    List<User> findAllByTenant_Id(Long tenantId);

}