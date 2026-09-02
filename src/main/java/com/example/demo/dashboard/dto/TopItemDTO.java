package com.example.demo.dashboard.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TopItemDTO {
    private String publicId;
    private String name;
    private Long quantitySold;
    private BigDecimal totalRevenue;
}