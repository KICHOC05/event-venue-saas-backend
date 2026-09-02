package com.example.demo.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderHistoryItemResponse {

    private String productName;
    private String productType;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String childName;
    private LocalDateTime sessionStart;
    private LocalDateTime sessionEnd;
    private Integer durationMinutes;
}
