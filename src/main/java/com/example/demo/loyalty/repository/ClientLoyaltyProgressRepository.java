package com.example.demo.loyalty.repository;

import com.example.demo.loyalty.model.ClientLoyaltyProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientLoyaltyProgressRepository extends JpaRepository<ClientLoyaltyProgress, Long> {

    Optional<ClientLoyaltyProgress> findByClient_IdAndLoyaltyProgram_Id(
            Long clientId, Long programId);
}
