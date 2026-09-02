package com.example.demo.auth.controller;

import com.example.demo.auth.dto.*;
import com.example.demo.auth.exception.InvalidCredentialsException;
import com.example.demo.auth.ratelimit.LoginRateLimitService;
import com.example.demo.auth.service.AuthService;
import com.example.demo.security.TenantContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimitService loginRateLimitService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {

        String clientAddress = resolveClientAddress(servletRequest);
        loginRateLimitService.checkAllowed(clientAddress);

        try {
            LoginResponse response = authService.login(request);
            loginRateLimitService.recordSuccess(clientAddress);
            return ResponseEntity.ok(response);
        } catch (InvalidCredentialsException ex) {
            loginRateLimitService.recordFailure(clientAddress);
            throw ex;
        }
    }

    private String resolveClientAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank()
                ? "unknown"
                : remoteAddress;
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LoginResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        return ResponseEntity.ok(
                authService.register(request, TenantContext.getTenantId())
        );
    }
}
