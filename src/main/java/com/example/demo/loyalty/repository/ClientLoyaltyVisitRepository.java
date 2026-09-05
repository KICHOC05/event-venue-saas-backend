package com.example.demo.loyalty.repository;

import com.example.demo.loyalty.model.ClientLoyaltyVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ClientLoyaltyVisitRepository extends JpaRepository<ClientLoyaltyVisit, Long> {

    boolean existsByOrderItem_Id(Long orderItemId);

    @Query("SELECT v.orderItem.id FROM ClientLoyaltyVisit v WHERE v.order.id = :orderId")
    Set<Long> findVisitedOrderItemIds(@Param("orderId") Long orderId);

    List<ClientLoyaltyVisit> findByClient_IdAndLoyaltyProgram_Id(Long clientId, Long programId);

    long countByClient_IdAndLoyaltyProgram_Id(Long clientId, Long programId);
}
