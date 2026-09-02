package com.example.demo.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String token;

    private String name;
    private String email;
    private String role;

    private String userPublicId;

    private Long tenantId;
    private Long branchId;

    private String businessName;
    private String branchName;
}