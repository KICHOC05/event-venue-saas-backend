package com.example.demo.settings.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TaxSettingsRequest {

    private Boolean taxEnabled;
    private BigDecimal taxRate;
}