package com.example.demo.order.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TimerHistoryFilterRequest {

    private String search;

    private String status;

    private Integer page = 0;

    private Integer size = 10;
}
