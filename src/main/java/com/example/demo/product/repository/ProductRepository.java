package com.example.demo.product.repository;

import com.example.demo.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByPublicIdAndTenant_IdAndActiveTrue(
            String publicId, Long tenantId);

    List<Product> findAllByTenant_IdAndActiveTrue(Long tenantId);

    Optional<Product> findByPublicIdAndTenant_Id(
            String publicId, Long tenantId);

    List<Product> findAllByTenant_Id(Long tenantId);
}