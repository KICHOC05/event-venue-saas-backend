package com.example.demo.dashboard.controller;

import com.example.demo.dashboard.dto.DashboardResponse;
import com.example.demo.dashboard.dto.StatsResponse;
import com.example.demo.dashboard.service.DashboardService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<StatsResponse> getStats(
            @RequestParam(value = "range", defaultValue = "7") Integer range) {
        return ResponseEntity.ok(dashboardService.getStats(range));
    }
}