package com.example.demo.loyalty.dto;

import lombok.Data;

@Data
public class RedeemRewardRequest {

    private String orderPublicId;
    private String clientPublicId;
}
