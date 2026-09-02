package com.example.demo.client.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ClientResponse {

    private String publicId;
    private String parentName;
    private String childName;
    private String phone;
    private String email;
    private LocalDate childBirthDate;
    private String notes;
    private Boolean frequent;
    private String status;
    private LocalDateTime createdAt;

    private Integer currentCount;
    private Integer requiredCount;
    private Long rewardsEarned;
    private Long rewardsAvailable;
    private Long rewardsRedeemed;
    private LocalDateTime lastVisitAt;
}
