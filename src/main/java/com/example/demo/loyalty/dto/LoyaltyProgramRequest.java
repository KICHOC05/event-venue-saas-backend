package com.example.demo.loyalty.dto;

import lombok.Data;

@Data
public class LoyaltyProgramRequest {

    private String name;
    private String description;
    private String qualifyingProductPublicId;
    private Integer requiredPurchases;
    private Integer rewardQuantity;
    private String rewardDescription;
    private Boolean active;
}
