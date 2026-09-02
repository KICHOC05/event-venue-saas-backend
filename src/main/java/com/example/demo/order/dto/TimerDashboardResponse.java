package com.example.demo.order.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class TimerDashboardResponse {

    private Long activeSessions;

    private Long expiringSoon;

    private Long finishedToday;

    private Long expired;

    private Long totalTodayMinutes;
}
