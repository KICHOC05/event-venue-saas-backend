package com.example.demo.order.dto;

import com.example.demo.common.enums.OrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class OrderResponse {

    private String publicId;
    private Long orderNumber;
    private String shortCode;

    private OrderStatus status;

    private String customerName;

    private String childName;

    private BigDecimal totalAmount;
    private BigDecimal subtotal;
    private BigDecimal tax;

    private LocalDateTime createdAt;

    private LocalDateTime closedAt;

    private String sellerName;
    private String sellerPublicId;
    private String sellerEmail;
    private String branchPublicId;
    private String branchName;

    private List<String> paymentMethods;

    private List<String> childNames;

    private String clientPublicId;

    private String clientParentName;

    private List<OrderItemResponse> items;

    private List<OrderPaymentDetailResponse> payments;

}
