package com.example.demo.product.service;

import com.example.demo.security.TenantContext;
import com.example.demo.common.enums.ProductType;
import com.example.demo.product.dto.ProductRequest;
import com.example.demo.product.dto.ProductResponse;
import com.example.demo.product.model.Product;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;

    public ProductResponse create(ProductRequest request) {
        Long tenantId = TenantContext.getTenantId();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

        Product product = new Product();

        product.setTenant(tenant);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setType(request.getType());
        product.setDepartment(request.getDepartment());
        product.setDurationMinutes(request.getDurationMinutes());
        product.setRequiresSchedule(request.getRequiresSchedule());
        product.setActive(true);

        applyBusinessRules(product, request);

        productRepository.save(product);
        return mapToResponse(product);
    }

    public List<ProductResponse> findAll() {
        Long tenantId = TenantContext.getTenantId();
        return productRepository.findAllByTenant_Id(tenantId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ProductResponse findByPublicId(String publicId) {
        Long tenantId = TenantContext.getTenantId();
        Product product = productRepository
                .findByPublicIdAndTenant_IdAndActiveTrue(publicId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        return mapToResponse(product);
    }

    public ProductResponse update(String publicId, ProductRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Product product = productRepository
                .findByPublicIdAndTenant_IdAndActiveTrue(publicId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setType(request.getType());
        product.setDepartment(request.getDepartment());
        product.setDurationMinutes(request.getDurationMinutes());
        product.setRequiresSchedule(request.getRequiresSchedule());

        productRepository.save(product);
        return mapToResponse(product);
    }

    public void delete(String publicId) {
        Long tenantId = TenantContext.getTenantId();
        Product product = productRepository
                .findByPublicIdAndTenant_IdAndActiveTrue(publicId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        product.setActive(false);
        productRepository.save(product);
    }

    // =========================
    // 🔹 TOGGLE STATUS
    // =========================
    public ProductResponse toggleStatus(String publicId) {

        Long tenantId = TenantContext.getTenantId();

        Product product = productRepository
                .findByPublicIdAndTenant_Id(publicId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        product.setActive(!product.getActive());

        productRepository.save(product);

        return mapToResponse(product);
    }

    // =========================
    // 🔥 VALIDACIONES
    // =========================
    private void validateRequest(ProductRequest request) {

        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalStateException("El nombre es obligatorio");
        }

        if (request.getPrice() == null) {
            throw new IllegalStateException("El precio es obligatorio");
        }

        if (request.getType() == null) {
            throw new IllegalStateException("El tipo de producto es obligatorio");
        }
    }

    // =========================
    // 🔥 BUSINESS RULES
    // =========================
    private void applyBusinessRules(Product product, ProductRequest request) {

        ProductType type = request.getType();

        // =========================
        // SERVICE → TIMER
        // =========================
        if (ProductType.SERVICE.equals(type)) {

            if (request.getDurationMinutes() == null || request.getDurationMinutes() <= 0) {
                throw new IllegalStateException("SERVICE requiere durationMinutes válido");
            }

            product.setDurationMinutes(request.getDurationMinutes());
            product.setRequiresSchedule(null);
            product.setStock(null);

        }

        // =========================
        // PACKAGE → EVENTO
        // =========================
        else if (ProductType.PACKAGE.equals(type)) {

            product.setRequiresSchedule(
                    request.getRequiresSchedule() != null
                            ? request.getRequiresSchedule()
                            : true
            );

            product.setDurationMinutes(null);
            product.setStock(null);

        }

        // =========================
        // PRODUCT → INVENTARIO
        // =========================
        else {

            if (request.getStock() == null) {
                throw new IllegalStateException("PRODUCT requiere stock");
            }

            product.setStock(request.getStock());
            product.setDurationMinutes(null);
            product.setRequiresSchedule(null);
        }
    }

    // =========================
    // 🔹 MAPPER
    // =========================
    private ProductResponse mapToResponse(Product product) {
        ProductResponse response = new ProductResponse();

        response.setPublicId(product.getPublicId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice());
        response.setStock(product.getStock());
        response.setType(product.getType());
        response.setActive(product.getActive());
        response.setDepartment(product.getDepartment());
        response.setDurationMinutes(product.getDurationMinutes());
        response.setRequiresSchedule(product.getRequiresSchedule());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        return response;
    }
}