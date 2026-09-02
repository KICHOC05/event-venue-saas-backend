package com.example.demo.user.controller;

import com.example.demo.user.dto.CreateUserRequest;
import com.example.demo.user.dto.UpdateUserRequest;
import com.example.demo.user.dto.UserResponse;
import com.example.demo.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // =========================================
    // CREATE USER (ADMIN ONLY)
    // =========================================
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request) {

        return ResponseEntity.ok(userService.create(request));
    }

    // =========================================
    // LIST USERS (ADMIN / MANAGER)
    // =========================================
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<UserResponse>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    // =========================================
    // GET BY PUBLIC ID
    // =========================================
    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<UserResponse> findByPublicId(
            @PathVariable String publicId) {

        return ResponseEntity.ok(userService.findByPublicId(publicId));
    }

    // =========================================
    // UPDATE USER
    // =========================================
    @PutMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> update(
            @PathVariable String publicId,
            @Valid @RequestBody UpdateUserRequest request) {

        return ResponseEntity.ok(userService.update(publicId, request));
    }

    // =========================================
    // CHANGE PASSWORD
    // =========================================
    @PatchMapping("/{publicId}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> changePassword(
            @PathVariable String publicId,
            @RequestParam String newPassword) {

        userService.changePassword(publicId, newPassword);
        return ResponseEntity.ok().build();
    }

    // =========================================
    // DEACTIVATE USER
    // =========================================
    @PatchMapping("/{publicId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(
            @PathVariable String publicId) {

        userService.deactivate(publicId);
        return ResponseEntity.ok().build();
    }

    // =========================================
    // DELETE USER (HARD DELETE - ADMIN ONLY)
    // =========================================
    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable String publicId) {

        userService.delete(publicId);
        return ResponseEntity.noContent().build();
    }
}