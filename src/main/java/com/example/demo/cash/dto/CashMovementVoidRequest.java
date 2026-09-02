package com.example.demo.cash.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CashMovementVoidRequest {

    @NotBlank(message = "voidReason es obligatorio")
    @Size(max = 500, message = "voidReason no puede exceder 500 caracteres")
    private String reason;
}
