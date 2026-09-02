package com.example.demo.client.model;

import com.example.demo.branch.model.Branch;
import com.example.demo.tenant.model.Tenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Getter
@Setter
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String publicId;

    @PrePersist
    public void generatePublicId() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID().toString();
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Branch branch;

    @Column(length = 200)
    private String parentName;

    @Column(length = 150)
    private String childName;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    private LocalDate childBirthDate;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private Boolean frequent = false;

    @Column(name = "frequent_program_consent_at")
    private LocalDateTime frequentProgramConsentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientStatus status = ClientStatus.ACTIVE;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;
}
