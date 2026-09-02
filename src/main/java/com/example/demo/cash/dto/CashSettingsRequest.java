package com.example.demo.cash.dto;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CashSettingsRequest {

    @NotNull(message = "defaultOpeningAmount es obligatorio")
    private BigDecimal defaultOpeningAmount;
}
