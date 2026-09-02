package com.example.demo.settings.model;

import com.example.demo.common.enums.InventoryMode;
import com.example.demo.tenant.model.Tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenant_settings")
@Getter
@Setter
public class TenantSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // relación con tenant
    @OneToOne
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    // modo de inventario
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryMode inventoryMode = InventoryMode.WARNING;

}