package com.example.demo.order.controller;

import com.example.demo.order.dto.ActiveSessionResponse;
import com.example.demo.order.dto.TimerDashboardResponse;
import com.example.demo.order.dto.TimerHistoryResponse;
import com.example.demo.order.service.TimerService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/timers")
public class TimerController {

    private final TimerService timerService;

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public List<ActiveSessionResponse> getActiveSessions() {

        return timerService.getActiveSessions();
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public Page<TimerHistoryResponse> getSessionHistory(

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            LocalDate date,

            @RequestParam(defaultValue = "0")
            Integer page,

            @RequestParam(defaultValue = "10")
            Integer size
    ) {

        int normalizedPage =
                Math.max(
                        page != null ? page : 0,
                        0);

        int normalizedSize =
                Math.max(
                        size != null ? size : 10,
                        1);

        normalizedSize =
                Math.min(
                        normalizedSize,
                        100);

        return timerService.getSessionHistory(
                search,
                status,
                date,
                normalizedPage,
                normalizedSize
        );
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public TimerDashboardResponse getDashboard() {

        return timerService.getTimersDashboard();
    }
}