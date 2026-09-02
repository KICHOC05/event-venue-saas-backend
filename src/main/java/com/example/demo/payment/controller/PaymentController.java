package com.example.demo.payment.controller;

import com.example.demo.payment.dto.PaymentRequest;
import com.example.demo.payment.dto.PaymentResponse;
import com.example.demo.payment.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{orderPublicId}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public PaymentResponse registerPayment(
            @PathVariable String orderPublicId,
            @Valid @RequestBody PaymentRequest request) {

        return paymentService.registerPayment(orderPublicId, request);
    }
}