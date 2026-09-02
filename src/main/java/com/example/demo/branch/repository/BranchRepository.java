package com.example.demo.branch.repository;

import com.example.demo.branch.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    Optional<Branch> findByPublicIdAndTenant_Id(String publicId, Long tenantId);

    Optional<Branch> findByIdAndTenant_Id(Long id, Long tenantId);

    List<Branch> findAllByTenant_Id(Long tenantId);

    Optional<Branch> findFirstByTenant_IdOrderByIdAsc(Long tenantId);

    boolean existsByTenant_IdAndName(Long tenantId, String name);
}
