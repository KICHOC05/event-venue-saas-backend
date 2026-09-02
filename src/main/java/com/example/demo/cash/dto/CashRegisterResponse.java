package com.example.demo.cash.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CashRegisterResponse {

    private String publicId;
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
    private BigDecimal expectedCash;
    private BigDecimal expectedAmount;

    private BigDecimal depositTotal;
    private BigDecimal withdrawalTotal;

    private BigDecimal countedAmount;
    private BigDecimal difference;

    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private String status;
}
