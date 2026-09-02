package com.example.demo.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class FinancialAuditEntryResponse {
    private String source;
    private String type;
    private String reference;
    private LocalDateTime date;
    private BigDecimal amount;
    private String paymentMethod;
    private String userPublicId;
    private String userName;
    private String userEmail;
    private String branchPublicId;
    private String branchName;
    private String operationPublicId;
    private String entryPublicId;
    private String cashRegisterPublicId;
}
