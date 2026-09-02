package com.example.demo.cash.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CloseCashRequest {

    private BigDecimal countedCash;

}