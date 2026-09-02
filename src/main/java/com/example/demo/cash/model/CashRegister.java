package com.example.demo.cash.model;

import com.example.demo.branch.model.Branch;
import com.example.demo.common.enums.CashStatus;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.user.model.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cash_registers", indexes = {
        @Index(name = "idx_cash_registers_tenant_branch_opened", columnList = "tenant_id,branch_id,opened_at"),
        @Index(name = "idx_cash_registers_tenant_status_opened", columnList = "tenant_id,status,opened_at")
})
@Getter
@Setter
public class CashRegister {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String publicId;

    @PrePersist
    public void generatePublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by", nullable = false)
    private User openedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    @Column(nullable = false)
    private BigDecimal openingAmount;

    private BigDecimal closingAmount;

    private BigDecimal expectedAmount;

    private BigDecimal difference;

    @Column(nullable = false)
    private LocalDateTime openedAt;

    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashStatus status;
}
