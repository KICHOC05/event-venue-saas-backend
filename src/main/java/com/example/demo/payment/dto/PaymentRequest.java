package com.example.demo.payment.dto;

import com.example.demo.common.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {

    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String reference;
}