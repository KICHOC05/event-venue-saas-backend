package com.example.demo.loyalty.model;

import com.example.demo.branch.model.Branch;
import com.example.demo.client.model.Client;
import com.example.demo.tenant.model.Tenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_loyalty_progress")
@Getter
@Setter
public class ClientLoyaltyProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private String publicId;

    @PrePersist
    public void generatePublicId() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID().toString();
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loyalty_program_id")
    private LoyaltyProgram loyaltyProgram;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "current_count")
    private Integer currentCount = 0;

    @Column(name = "required_count")
    private Integer requiredCount = 5;

    @Column(name = "rewards_earned")
    private Long rewardsEarned = 0L;

    @Column(name = "rewards_available")
    private Long rewardsAvailable = 0L;

    @Column(name = "rewards_redeemed")
    private Long rewardsRedeemed = 0L;

    @Column(name = "last_visit_at")
    private LocalDateTime lastVisitAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
