package com.example.demo.settings.model;

import com.example.demo.tenant.model.Tenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "tax_settings")
@Getter
@Setter
public class TaxSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private Boolean taxEnabled = true;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate = new BigDecimal("0.16");
}