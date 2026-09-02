package com.example.demo.branch.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.security.TenantContext;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchRepository branchRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<Map<String, Object>>> findAll() {
        Long tenantId = TenantContext.getTenantId();

        List<Map<String, Object>> branches = branchRepository
                .findAllByTenant_Id(tenantId)
                .stream()
                .map(b -> Map.<String, Object>of(
                        "id", b.getId(),
                        "publicId", b.getPublicId(),
                        "name", b.getName(),
                        "address", b.getAddress() != null ? b.getAddress() : "",
                        "phone", b.getPhone() != null ? b.getPhone() : ""))
                .toList();

        return ResponseEntity.ok(branches);
    }
}
