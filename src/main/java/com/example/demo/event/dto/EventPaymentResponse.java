package com.example.demo.event.dto;

import com.example.demo.common.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventPaymentResponse {

    private String publicId;
    private String eventPublicId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String reference;
    private String notes;
    private String receivedByUserPublicId;
    private String receivedByUserEmail;
    private LocalDateTime paidAt;
    private BigDecimal previousBalance;
    private BigDecimal totalPaid;
    private BigDecimal remainingAmount;
    private Boolean fullyPaid;
}
