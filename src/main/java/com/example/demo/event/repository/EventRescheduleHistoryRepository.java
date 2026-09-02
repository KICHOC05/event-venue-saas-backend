package com.example.demo.event.repository;

import com.example.demo.event.model.EventRescheduleHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRescheduleHistoryRepository extends JpaRepository<EventRescheduleHistory, Long> {

    List<EventRescheduleHistory> findByEventBooking_PublicIdAndTenant_IdOrderByChangedAtDesc(
            String eventPublicId, Long tenantId
    );
}
