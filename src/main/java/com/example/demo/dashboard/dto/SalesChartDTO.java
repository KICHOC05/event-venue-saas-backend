package com.example.demo.dashboard.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SalesChartDTO {
    private List<String> labels;
    private List<BigDecimal> data;
    private List<String> fullDates;
}