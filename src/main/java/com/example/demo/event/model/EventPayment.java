package com.example.demo.event.model;

import com.example.demo.branch.model.Branch;
import com.example.demo.cash.model.CashRegister;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.tenant.model.Tenant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_payments", indexes = {
        @Index(name = "idx_event_payments_tenant_branch_paid", columnList = "tenant_id,branch_id,paid_at"),
        @Index(name = "idx_event_payments_tenant_method_paid", columnList = "tenant_id,payment_method,paid_at"),
        @Index(name = "idx_event_payments_cash_method", columnList = "cash_register_id,payment_method")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventPayment {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_register_id")
    private CashRegister cashRegister;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "event_price_at_payment", precision = 10, scale = 2)
    private BigDecimal eventPriceAtPayment;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    private String reference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "received_by_user_public_id")
    private String receivedByUserPublicId;

    @Column(name = "received_by_user_email")
    private String receivedByUserEmail;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
    }
}
