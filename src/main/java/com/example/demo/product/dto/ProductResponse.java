package com.example.demo.product.dto;

import com.example.demo.common.enums.ProductType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ProductResponse {

    private String publicId;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private ProductType type;
    private Boolean active;
    private String department;
    private Integer durationMinutes;
    private Boolean requiresSchedule;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}