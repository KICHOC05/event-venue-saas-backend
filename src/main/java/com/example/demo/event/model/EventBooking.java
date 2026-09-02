package com.example.demo.event.model;

import com.example.demo.branch.model.Branch;
import com.example.demo.common.enums.EventStatus;
import com.example.demo.product.model.Product;
import com.example.demo.tenant.model.Tenant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
        name = "event_bookings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_event_booking_branch_number",
                columnNames = {"tenant_id", "branch_id", "event_number"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    // Nullable únicamente para permitir el backfill seguro de eventos históricos.
    // Todos los eventos nuevos reciben el valor antes de persistirse.
    @Column(name = "event_number")
    private Long eventNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_product_id", nullable = false)
    private Product packageProduct;

    @Column(nullable = false)
    private String customerName;

    private String phone;

    @Column(nullable = false)
    private String childName;

    private Integer childAge;

    @Column(nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    private Integer guestChildren;

    private Integer guestAdults;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private BigDecimal eventPrice;

    @Column(nullable = false)
    private BigDecimal depositAmount;

    @Column(nullable = false)
    private BigDecimal remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
    }
}
