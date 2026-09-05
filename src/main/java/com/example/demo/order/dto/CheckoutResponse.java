package com.example.demo.order.dto;

import com.example.demo.payment.dto.PaymentResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckoutResponse {
    private OrderResponse order;
    private PaymentResponse payment;
    private boolean closed;
}
