package com.example.demo.order.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class OrderItemResponse {

  private String productPublicId;

  private String publicId;

  private String productName;

  private String childName;

  private Integer quantity;

  private BigDecimal unitPrice;

  private BigDecimal subtotal;

  private String warning;

  private String status;

    private LocalDateTime sessionStart;

    private LocalDateTime sessionEnd;

    private Integer durationMinutes;
    
    private Boolean active;

}