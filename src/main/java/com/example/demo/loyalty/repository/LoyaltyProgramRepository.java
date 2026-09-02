package com.example.demo.loyalty.repository;

import com.example.demo.loyalty.model.LoyaltyProgram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoyaltyProgramRepository extends JpaRepository<LoyaltyProgram, Long> {

    Optional<LoyaltyProgram> findByTenant_IdAndBranch_Id(Long tenantId, Long branchId);
}
