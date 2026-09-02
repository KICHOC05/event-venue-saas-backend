package com.example.demo.loyalty.repository;

import com.example.demo.loyalty.model.ClientRewardRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientRewardRedemptionRepository extends JpaRepository<ClientRewardRedemption, Long> {

    List<ClientRewardRedemption> findByClient_IdAndLoyaltyProgram_IdAndStatus(
            Long clientId, Long programId,
            com.example.demo.loyalty.model.RedemptionStatus status);

    long countByClient_IdAndLoyaltyProgram_IdAndStatus(
            Long clientId, Long programId,
            com.example.demo.loyalty.model.RedemptionStatus status);
}
