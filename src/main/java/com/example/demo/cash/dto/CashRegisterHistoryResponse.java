package com.example.demo.cash.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CashRegisterHistoryResponse {

    private String publicId;
    private String status;
    private BigDecimal openingAmount;
    private BigDecimal closingAmount;
    private BigDecimal expectedAmount;
    private BigDecimal difference;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private String openedByName;
    private String openedByPublicId;
    private String openedByEmail;
    private String closedByName;
    private String closedByPublicId;
    private String closedByEmail;
    private String branchPublicId;
    private String branchName;
    private BigDecimal cashSales;
    private BigDecimal cardSales;
    private BigDecimal transferSales;
    private BigDecimal depositTotal;
    private BigDecimal withdrawalTotal;
    private long orderCount;
    private int movementCount;
}
