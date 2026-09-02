package com.example.demo.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderHistoryResponse {

    private String publicId;
    private String shortCode;
    private Long orderNumber;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private String customerName;
    private String sellerName;
    private String sellerPublicId;
    private String sellerEmail;
    private String branchPublicId;
    private String branchName;
    private String status;
    private BigDecimal totalAmount;
    private List<String> paymentMethods;
    private List<String> childNames;
    private Long itemsCount;

    private String clientPublicId;
    private String clientParentName;

    private List<OrderHistoryItemResponse> items;
}
