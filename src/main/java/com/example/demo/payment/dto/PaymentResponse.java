package com.example.demo.payment.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PaymentResponse {

    private BigDecimal orderTotal;
    private BigDecimal totalPaid;
    private BigDecimal remainingAmount;
    private BigDecimal change;

    private BigDecimal amountReceived;
    private BigDecimal amountApplied;
    private String paymentMethod;
}