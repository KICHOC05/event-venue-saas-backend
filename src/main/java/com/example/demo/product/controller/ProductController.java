package com.example.demo.product.controller;

import com.example.demo.product.dto.ProductRequest;
import com.example.demo.product.dto.ProductResponse;
import com.example.demo.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PostMapping
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    @GetMapping
    public List<ProductResponse> findAll() {
        return productService.findAll();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    @GetMapping("/{publicId}")
    public ProductResponse findByPublicId(@PathVariable String publicId) {
        return productService.findByPublicId(publicId);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PutMapping("/{publicId}")
    public ProductResponse update(
            @PathVariable String publicId,
            @Valid @RequestBody ProductRequest request) {
        return productService.update(publicId, request);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @DeleteMapping("/{publicId}")
    public void delete(@PathVariable String publicId) {
        productService.delete(publicId);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PatchMapping("/{publicId}/toggle-status")
    public ProductResponse toggleStatus(@PathVariable String publicId) {
        return productService.toggleStatus(publicId);
    }
}