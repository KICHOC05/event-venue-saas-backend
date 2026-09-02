package com.example.demo.user.dto;

import com.example.demo.common.enums.UserRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponse {

    private String publicId;
    private String name;
    private String email;
    private UserRole role;
    private Boolean active;

    private Long branchId;
    private String branchName;

    private LocalDateTime createdAt;
}