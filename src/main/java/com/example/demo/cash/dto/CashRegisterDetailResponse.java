package com.example.demo.cash.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CashRegisterDetailResponse {

    private String publicId;
    private String status;
    private BigDecimal openingAmount;
    private BigDecimal cashSales;
    private BigDecimal cardSales;
    private BigDecimal transferSales;
    private BigDecimal posCashSales;
    private BigDecimal eventCashPayments;
    private BigDecimal posCardSales;
    private BigDecimal eventCardPayments;
    private BigDecimal posTransferSales;
    private BigDecimal eventTransferPayments;
    private BigDecimal salesTotal;
    private BigDecimal depositTotal;
    private BigDecimal withdrawalTotal;
    private BigDecimal expectedCash;
    private BigDecimal countedCash;
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
    private long orderCount;
    private int movementCount;
}
