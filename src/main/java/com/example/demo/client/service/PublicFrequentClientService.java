package com.example.demo.client.service;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.client.dto.PublicFrequentClientRegistrationRequest;
import com.example.demo.client.dto.PublicFrequentClientRegistrationResponse;
import com.example.demo.client.model.Client;
import com.example.demo.client.model.ClientStatus;
import com.example.demo.client.repository.ClientRepository;
import com.example.demo.common.enums.TenantStatus;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PublicFrequentClientService {

    private static final String SUCCESS_MESSAGE =
            "Tu registro como cliente frecuente quedó activo";

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final ClientRepository clientRepository;

    @Transactional
    public PublicFrequentClientRegistrationResponse register(
            String tenantPublicId,
            PublicFrequentClientRegistrationRequest request) {
        if (!StringUtils.hasText(tenantPublicId)) {
            throw new IllegalArgumentException("tenantPublicId es obligatorio");
        }

        Tenant tenant = tenantRepository
                .findByPublicIdAndStatus(tenantPublicId.trim(), TenantStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Negocio no encontrado"));

        String normalizedPhone = normalizePhone(request.getPhone());
        LocalDateTime now = LocalDateTime.now();

        Client client = clientRepository
                .findFirstByTenant_IdAndPhoneOrderByIdAsc(tenant.getId(), normalizedPhone)
                .orElseGet(() -> newClient(tenant));

        if (client.getId() == null) {
            Branch primaryBranch = branchRepository
                    .findFirstByTenant_IdOrderByIdAsc(tenant.getId())
                    .orElseThrow(() -> new EntityNotFoundException("El negocio no tiene una sucursal disponible"));
            client.setBranch(primaryBranch);
        }

        if (!StringUtils.hasText(client.getParentName())) {
            client.setParentName(request.getParentName().trim());
        }
        if (!StringUtils.hasText(client.getChildName())) {
            client.setChildName(request.getChildName().trim());
        }
        if (!StringUtils.hasText(client.getEmail()) && StringUtils.hasText(request.getEmail())) {
            client.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        }

        client.setPhone(normalizedPhone);
        client.setFrequent(true);
        client.setStatus(ClientStatus.ACTIVE);
        client.setFrequentProgramConsentAt(now);
        client.setUpdatedAt(now);
        clientRepository.save(client);

        return PublicFrequentClientRegistrationResponse.builder()
                .status("ACTIVE")
                .message(SUCCESS_MESSAGE)
                .phoneVerificationRequired(false)
                .build();
    }

    private Client newClient(Tenant tenant) {
        Client client = new Client();
        client.setTenant(tenant);
        return client;
    }

    private String normalizePhone(String phone) {
        String normalized = phone == null ? "" : phone.replaceAll("\\D", "");
        if (normalized.length() < 10 || normalized.length() > 15) {
            throw new IllegalArgumentException("Ingresa un teléfono válido de 10 a 15 dígitos");
        }
        return normalized;
    }
}
