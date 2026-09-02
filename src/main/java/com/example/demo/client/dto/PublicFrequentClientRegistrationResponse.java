package com.example.demo.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PublicFrequentClientRegistrationResponse {

    private String status;
    private String message;
    private boolean phoneVerificationRequired;
}
