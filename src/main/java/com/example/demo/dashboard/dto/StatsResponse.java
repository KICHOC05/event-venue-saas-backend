package com.example.demo.dashboard.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class StatsResponse {

    private Integer rangeDays;
    private String dateFrom;
    private String dateTo;

    private SalesChartDTO dailySales;

    private List<TopItemDTO> salesByProduct;
    private List<TopItemDTO> salesByPackage;
    private List<TopItemDTO> topProducts;

    private BigDecimal totalSales;
    private BigDecimal posSales;
    private BigDecimal eventSales;
    private BigDecimal averageTicket;
    private Double growthPercentage;
    private Long totalOrders;
    private Long paidEvents;
    private Long totalTransactions;

    private Integer scheduledEvents;

    private PaymentBreakdown paymentBreakdown;

    @Data
    @Builder
    public static class PaymentBreakdown {
        private BigDecimal cashTotal;
        private BigDecimal cardTotal;
        private BigDecimal transferTotal;
    }
}
