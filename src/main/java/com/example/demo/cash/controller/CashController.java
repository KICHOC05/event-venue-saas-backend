package com.example.demo.cash.controller;

import com.example.demo.cash.dto.CashMovementRequest;
import com.example.demo.cash.dto.CashMovementResponse;
import com.example.demo.cash.dto.CashMovementVoidRequest;
import com.example.demo.cash.dto.CashRegisterDetailResponse;
import com.example.demo.cash.dto.CashRegisterHistoryResponse;
import com.example.demo.cash.dto.CashSettingsRequest;
import com.example.demo.cash.dto.CashSettingsResponse;
import com.example.demo.cash.dto.CloseCashRequest;
import com.example.demo.cash.dto.CashRegisterResponse;
import com.example.demo.cash.dto.OpenCashRequest;
import com.example.demo.cash.service.CashService;
import com.example.demo.cash.service.CashSettingsService;
import com.example.demo.common.enums.CashMovementType;
import com.example.demo.common.enums.CashStatus;
import com.example.demo.security.TenantContext;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/cash")
@RequiredArgsConstructor
public class CashController {

    private final CashService cashService;
    private final CashSettingsService cashSettingsService;


    @PostMapping("/open")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public CashRegisterResponse openCash(
            @Valid @RequestBody OpenCashRequest request) {

        return cashService.openCash(request);
    }


    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public CashRegisterResponse currentCash() {

        return cashService.currentCash();
    }


    @PostMapping("/close")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CashRegisterResponse closeCash(
            @Valid @RequestBody CloseCashRequest request) {

        return cashService.closeCash(request);
    }


    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public CashSettingsResponse getSettings() {
        return cashSettingsService.getSettings(
                TenantContext.getTenantId(),
                TenantContext.getBranchId());
    }


    @PutMapping("/settings/opening-amount")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CashSettingsResponse updateOpeningAmount(
            @Valid @RequestBody CashSettingsRequest request) {
        return cashSettingsService.updateOpeningAmount(
                TenantContext.getTenantId(),
                TenantContext.getBranchId(),
                TenantContext.getUserId(),
                request.getDefaultOpeningAmount());
    }


    @PostMapping("/movements/withdrawals")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CashMovementResponse withdraw(
            @Valid @RequestBody CashMovementRequest request) {

        return cashService.withdraw(request);
    }


    @PostMapping("/movements/deposits")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CashMovementResponse deposit(
            @Valid @RequestBody CashMovementRequest request) {

        return cashService.deposit(request);
    }


    @GetMapping("/movements/current")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public List<CashMovementResponse> getCurrentMovements() {
        return cashService.getCurrentMovements();
    }


    @GetMapping("/movements/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CashMovementResponse getMovementDetail(
            @PathVariable String publicId) {

        return cashService.getMovementDetail(publicId);
    }


    @PatchMapping("/movements/{publicId}/void")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CashMovementResponse voidMovement(
            @PathVariable String publicId,
            @Valid @RequestBody CashMovementVoidRequest request) {

        return cashService.voidMovement(publicId, request);
    }


    @GetMapping("/movements")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Page<CashMovementResponse> getMovements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) CashMovementType type,
            @RequestParam(required = false) Boolean voided,
            @RequestParam(required = false) String userPublicId,
            @RequestParam(required = false) String branchPublicId,
            @RequestParam(required = false) String cashRegisterPublicId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        return cashService.getMovementHistory(page, size, type, voided, userPublicId,
                branchPublicId, cashRegisterPublicId, from, to);
    }


    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Page<CashRegisterHistoryResponse> getCashHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) CashStatus status,
            @RequestParam(required = false) String openedByPublicId,
            @RequestParam(required = false) String branchPublicId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        return cashService.getCashRegisterHistory(page, size, status, openedByPublicId,
                branchPublicId, from, to);
    }


    @GetMapping("/history/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CashRegisterDetailResponse getCashDetail(
            @PathVariable String publicId) {

        return cashService.getCashRegisterDetail(publicId);
    }
}
