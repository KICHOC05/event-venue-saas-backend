package com.example.demo.cash.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class OpenCashRequest {

    private BigDecimal openingAmount;

}