package com.example.demo.client.controller;

import com.example.demo.client.dto.ClientRequest;
import com.example.demo.client.dto.ClientResponse;
import com.example.demo.client.service.ClientService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public Page<ClientResponse> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean frequent) {
        return clientService.search(page, size, search, frequent);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public ClientResponse create(@RequestBody ClientRequest request) {
        return clientService.create(request);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','EMPLOYEE')")
    public ClientResponse getByPublicId(@PathVariable String publicId) {
        return clientService.getByPublicId(publicId);
    }
}
