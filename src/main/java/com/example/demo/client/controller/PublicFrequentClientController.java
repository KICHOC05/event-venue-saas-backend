package com.example.demo.client.controller;

import com.example.demo.client.dto.PublicFrequentClientRegistrationRequest;
import com.example.demo.client.dto.PublicFrequentClientRegistrationResponse;
import com.example.demo.client.service.PublicFrequentClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/frequent-clients")
@RequiredArgsConstructor
public class PublicFrequentClientController {

    private final PublicFrequentClientService publicFrequentClientService;

    @PostMapping("/registrations")
    public PublicFrequentClientRegistrationResponse register(
            @RequestParam String tenantPublicId,
            @Valid @RequestBody PublicFrequentClientRegistrationRequest request) {
        return publicFrequentClientService.register(tenantPublicId, request);
    }
}
