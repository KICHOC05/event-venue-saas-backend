package com.example.demo.loyalty.controller;

import com.example.demo.loyalty.dto.ClientLoyaltyResponse;
import com.example.demo.loyalty.dto.LoyaltyProgramRequest;
import com.example.demo.loyalty.dto.LoyaltyProgramResponse;
import com.example.demo.loyalty.dto.RedeemRewardRequest;
import com.example.demo.loyalty.service.LoyaltyService;
import com.example.demo.order.dto.OrderResponse;
import com.example.demo.order.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loyalty")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;
    private final OrderService orderService;

    @GetMapping("/program")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public LoyaltyProgramResponse getProgram() {
        return loyaltyService.getProgram();
    }

    @PutMapping("/program")
    @PreAuthorize("hasRole('ADMIN')")
    public LoyaltyProgramResponse updateProgram(@Valid @RequestBody LoyaltyProgramRequest request) {
        return loyaltyService.updateProgram(request);
    }

    @GetMapping("/clients/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public ClientLoyaltyResponse getClientLoyalty(@PathVariable String publicId) {
        return loyaltyService.getClientLoyalty(publicId);
    }

    @PostMapping("/redeem/{orderPublicId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public OrderResponse redeemReward(
            @PathVariable String orderPublicId,
            @Valid @RequestBody RedeemRewardRequest request) {
        loyaltyService.redeemReward(request, orderPublicId);
        return orderService.getOrder(orderPublicId);
    }
}
