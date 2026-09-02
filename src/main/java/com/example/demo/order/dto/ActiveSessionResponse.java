package com.example.demo.order.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActiveSessionResponse {

    private String itemPublicId;
    private String childName;
    private String productName;

    private LocalDateTime sessionStart;
    private LocalDateTime sessionEnd;

    private long remainingSeconds;
    private long remainingMinutes;

    private Integer durationMinutes;

    private boolean expiringSoon;

    private String status;

    private boolean expired;
    private int progressPercent;

    private String customerName;
    private String orderPublicId;
}
