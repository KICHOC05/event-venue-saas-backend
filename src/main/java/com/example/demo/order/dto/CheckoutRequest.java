package com.example.demo.order.dto;

import com.example.demo.payment.dto.PaymentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CheckoutRequest {

    @NotBlank(message = "El identificador del checkout es obligatorio")
    @Size(max = 100, message = "El identificador del checkout no puede superar 100 caracteres")
    private String requestId;

    @Valid
    @NotNull(message = "El pago es obligatorio")
    private PaymentRequest payment;
}
