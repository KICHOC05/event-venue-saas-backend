package com.example.demo.settings.dto;

import com.example.demo.common.enums.InventoryMode;

import lombok.*;

@Getter
@Setter
public class TenantSettingsResponse {

    private InventoryMode inventoryMode;
}