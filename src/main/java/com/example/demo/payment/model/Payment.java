package com.example.demo.payment.model;

import com.example.demo.branch.model.Branch;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.order.model.Order;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.user.model.User;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_tenant_branch_created", columnList = "tenant_id,branch_id,created_at"),
        @Index(name = "idx_payments_tenant_method_created", columnList = "tenant_id,payment_method,created_at"),
        @Index(name = "idx_payments_order_created", columnList = "order_id,created_at")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_received", precision = 38, scale = 2)
    private BigDecimal amountReceived;

    @Column(name = "change_amount", precision = 38, scale = 2)
    private BigDecimal changeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column
    private String reference;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
