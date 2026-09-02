package com.example.demo.cash.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CashMovementRequest {

    @NotNull
    @DecimalMin(value = "0.01", message = "amount debe ser mayor a 0")
    private BigDecimal amount;

    @NotBlank(message = "reason es obligatorio")
    private String reason;

    private String notes;
}
