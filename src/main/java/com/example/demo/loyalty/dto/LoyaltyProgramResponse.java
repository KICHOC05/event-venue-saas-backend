package com.example.demo.loyalty.dto;

import lombok.Data;

@Data
public class LoyaltyProgramResponse {

    private String publicId;
    private String name;
    private String description;
    private String qualifyingProductPublicId;
    private String qualifyingProductName;
    private Integer requiredPurchases;
    private Integer rewardQuantity;
    private String rewardDescription;
    private Boolean active;
}
