package com.example.demo.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.dto.CreateUserRequest;
import com.example.demo.user.dto.UpdateUserRequest;
import com.example.demo.user.dto.UserResponse;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    // ============================================================
    // CREATE USER (ADMIN)
    // ============================================================
    @Transactional
    public UserResponse create(CreateUserRequest request) {

        Long tenantId = TenantContext.getTenantId();

        if (tenantId == null)
            throw new BusinessException("Tenant no identificado");

        if (userRepository.existsByTenant_IdAndEmail(tenantId, request.getEmail())) {
            throw new BusinessException("El email ya existe en este tenant");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        Branch branch = branchRepository
                .findByIdAndTenant_Id(request.getBranchId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no válida"));

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setTenant(tenant);
        user.setBranch(branch);
        user.setActive(true);

        user = userRepository.save(user);

        return toResponse(user);
    }

    // ============================================================
    // LIST USERS (BY TENANT)
    // ============================================================
    public List<UserResponse> findAll() {

        Long tenantId = TenantContext.getTenantId();

        return userRepository.findAllByTenant_Id(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ============================================================
    // FIND BY PUBLIC ID (AISLADO)
    // ============================================================
    public UserResponse findByPublicId(String publicId) {

        Long tenantId = TenantContext.getTenantId();

        User user = userRepository
                .findByPublicIdAndTenant_Id(publicId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        return toResponse(user);
    }

    // ============================================================
    // UPDATE USER
    // ============================================================
    @Transactional
    public UserResponse update(String publicId, UpdateUserRequest request) {

        Long tenantId = TenantContext.getTenantId();

        User user = userRepository
                .findByPublicIdAndTenant_Id(publicId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (request.getName() != null)
            user.setName(request.getName());

        if (request.getRole() != null)
            user.setRole(request.getRole());

        if (request.getActive() != null)
            user.setActive(request.getActive());

        if (request.getBranchId() != null) {
            Branch branch = branchRepository
                    .findByIdAndTenant_Id(request.getBranchId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sucursal no válida"));

            user.setBranch(branch);
        }

        userRepository.save(user);

        return toResponse(user);
    }

    // ============================================================
    // CHANGE PASSWORD
    // ============================================================
    @Transactional
    public void changePassword(String publicId, String newPassword) {

        Long tenantId = TenantContext.getTenantId();

        User user = userRepository
                .findByPublicIdAndTenant_Id(publicId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        user.setPassword(passwordEncoder.encode(newPassword));

        userRepository.save(user);
    }

    // ============================================================
    // DELETE (SOFT DELETE RECOMENDADO)
    // ============================================================
    @Transactional
    public void deactivate(String publicId) {

        Long tenantId = TenantContext.getTenantId();

        User user = userRepository
                .findByPublicIdAndTenant_Id(publicId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        user.setActive(false);

        userRepository.save(user);
    }

    // ============================================================
    // DELETE (HARD DELETE)
    // ============================================================

    @Transactional
    public void delete(String publicId) {

        Long tenantId = TenantContext.getTenantId();

        User user = userRepository
                .findByPublicIdAndTenant_Id(publicId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // No permitir que un admin se elimine a sí mismo
        Long currentUserId = TenantContext.getUserId();
        if (user.getId().equals(currentUserId)) {
            throw new BusinessException("No puedes eliminarte a ti mismo");
        }

        userRepository.delete(user);
    }

    // ============================================================
    // MAPPER
    // ============================================================
    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .publicId(user.getPublicId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.getActive())
                .branchId(user.getBranch().getId())
                .branchName(user.getBranch().getName())
                .createdAt(user.getCreatedAt())
                .build();
    }
}