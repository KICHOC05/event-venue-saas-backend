package com.example.demo.event.model;

import com.example.demo.branch.model.Branch;
import com.example.demo.tenant.model.Tenant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "event_reschedule_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRescheduleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_booking_id", nullable = false)
    private EventBooking eventBooking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false)
    private LocalDate oldEventDate;

    @Column(nullable = false)
    private LocalTime oldStartTime;

    @Column(nullable = false)
    private LocalTime oldEndTime;

    @Column(nullable = false)
    private LocalDate newEventDate;

    @Column(nullable = false)
    private LocalTime newStartTime;

    @Column(nullable = false)
    private LocalTime newEndTime;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "changed_by_user_public_id")
    private String changedByUserPublicId;

    @Column(name = "changed_by_user_email")
    private String changedByUserEmail;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    public void prePersist() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
    }
}
