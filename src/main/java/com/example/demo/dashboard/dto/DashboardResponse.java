package com.example.demo.dashboard.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardResponse {

    private BigDecimal salesToday;
    private BigDecimal salesYesterday;
    private Double salesTodayGrowth;
    private BigDecimal posSalesToday;
    private BigDecimal eventSalesToday;

    private BigDecimal monthlyRevenue;
    private BigDecimal previousMonthRevenue;
    private Double monthlyGrowth;
    private BigDecimal posMonthlyRevenue;
    private BigDecimal eventMonthlyRevenue;

    private InventorySummary inventory;
    private SalesChartDTO salesChart;
    private List<TopItemDTO> topPackages;
    private List<UpcomingEventDTO> upcomingEvents;
    private Integer scheduledEventsCount;

    @Data
    @Builder
    public static class InventorySummary {
        private Integer totalProducts;
        private Integer totalStock;
        private Integer lowStockCount;
        private List<LowStockProductDTO> lowStockProducts;
    }

    @Data
    @Builder
    public static class LowStockProductDTO {
        private String publicId;
        private String name;
        private Integer stock;
    }

    @Data
    @Builder
    public static class UpcomingEventDTO {
        private String date;
        private String client;
        private String packageName;
        private Integer children;
        private String status;
    }
}
