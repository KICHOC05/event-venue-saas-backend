package com.example.demo.order.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TimerHistoryResponse {

    private String itemPublicId;

    private String orderPublicId;

    private String customerName;

    private String childName;

    private String productName;

    private LocalDateTime sessionStart;

    private LocalDateTime sessionEnd;

    private Integer durationMinutes;

    private String status;
}
