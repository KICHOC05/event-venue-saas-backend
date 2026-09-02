package com.example.demo.cash.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CashMovementResponse {

    private String publicId;
    private String type;
    private BigDecimal amount;
    private String reason;
    private String notes;
    private String userName;
    private String userPublicId;
    private String userEmail;
    private String cashRegisterPublicId;
    private String branchPublicId;
    private String branchName;
    private LocalDateTime createdAt;

    private Boolean voided;
    private LocalDateTime voidedAt;
    private String voidedByName;
    private String voidReason;
}
