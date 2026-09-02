package com.example.demo.loyalty.repository;

import com.example.demo.loyalty.model.ClientLoyaltyVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientLoyaltyVisitRepository extends JpaRepository<ClientLoyaltyVisit, Long> {

    boolean existsByOrderItem_Id(Long orderItemId);

    List<ClientLoyaltyVisit> findByClient_IdAndLoyaltyProgram_Id(Long clientId, Long programId);

    long countByClient_IdAndLoyaltyProgram_Id(Long clientId, Long programId);
}
