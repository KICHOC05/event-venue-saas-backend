package com.example.demo.loyalty.dto;

import lombok.Data;

@Data
public class ClientLoyaltyResponse {

    private long totalVisits;
    private int requiredPurchases;
    private long rewardsEarned;
    private long rewardsAvailable;
    private long rewardsRedeemed;
    private int nextRewardAt;
}
