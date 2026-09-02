package com.example.demo.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderPaymentDetailResponse {
    private String publicId;
    private String paymentMethod;
    private BigDecimal amount;
    private BigDecimal amountReceived;
    private BigDecimal changeAmount;
    private String reference;
    private LocalDateTime createdAt;
    private String receivedByPublicId;
    private String receivedByName;
    private String receivedByEmail;
}
