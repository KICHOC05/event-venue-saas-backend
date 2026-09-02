package com.example.demo.client.service;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.client.dto.ClientRequest;
import com.example.demo.client.dto.ClientResponse;
import com.example.demo.client.model.Client;
import com.example.demo.client.repository.ClientRepository;
import com.example.demo.loyalty.model.ClientLoyaltyProgress;
import com.example.demo.loyalty.model.LoyaltyProgram;
import com.example.demo.loyalty.repository.ClientLoyaltyProgressRepository;
import com.example.demo.loyalty.repository.LoyaltyProgramRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final LoyaltyProgramRepository loyaltyProgramRepository;
    private final ClientLoyaltyProgressRepository loyaltyProgressRepository;

    @Transactional(readOnly = true)
    public Page<ClientResponse> search(int page, int size, String search, Boolean frequent) {
        Long tenantId = TenantContext.getTenantId();
        Pageable pageable = PageRequest.of(page, size);
        return clientRepository.searchByTenant(tenantId, search, frequent, pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found"));

        Client client = new Client();
        client.setTenant(tenant);
        client.setBranch(branch);
        client.setParentName(request.getParentName());
        client.setChildName(request.getChildName());
        client.setPhone(request.getPhone());
        client.setEmail(request.getEmail());
        client.setChildBirthDate(request.getChildBirthDate());
        client.setNotes(request.getNotes());
        client.setFrequent(request.getFrequent() != null ? request.getFrequent() : false);
        client.setUpdatedAt(LocalDateTime.now());

        clientRepository.save(client);

        return mapToResponse(client);
    }

    @Transactional(readOnly = true)
    public ClientResponse getByPublicId(String publicId) {
        Long tenantId = TenantContext.getTenantId();
        Client client = clientRepository.findByPublicIdAndTenant_Id(publicId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));
        return mapToResponse(client);
    }

    private void populateLoyaltyProgress(ClientResponse r, Client client) {
        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();

        LoyaltyProgram program = loyaltyProgramRepository
                .findByTenant_IdAndBranch_Id(tenantId, branchId)
                .orElse(null);

        int required = program != null ? program.getRequiredPurchases() : 5;

        if (program != null && Boolean.TRUE.equals(program.getActive())) {
            ClientLoyaltyProgress progress = loyaltyProgressRepository
                    .findByClient_IdAndLoyaltyProgram_Id(client.getId(), program.getId())
                    .orElse(null);

            if (progress != null) {
                r.setCurrentCount(progress.getCurrentCount());
                r.setRequiredCount(progress.getRequiredCount() != null ? progress.getRequiredCount() : required);
                r.setRewardsEarned(progress.getRewardsEarned());
                r.setRewardsAvailable(progress.getRewardsAvailable());
                r.setRewardsRedeemed(progress.getRewardsRedeemed());
                r.setLastVisitAt(progress.getLastVisitAt());
                return;
            }
        }

        r.setCurrentCount(0);
        r.setRequiredCount(required);
        r.setRewardsEarned(0L);
        r.setRewardsAvailable(0L);
        r.setRewardsRedeemed(0L);
        r.setLastVisitAt(null);
    }

    private ClientResponse mapToResponse(Client client) {
        ClientResponse r = new ClientResponse();
        r.setPublicId(client.getPublicId());
        r.setParentName(client.getParentName());
        r.setChildName(client.getChildName());
        r.setPhone(client.getPhone());
        r.setEmail(client.getEmail());
        r.setChildBirthDate(client.getChildBirthDate());
        r.setNotes(client.getNotes());
        r.setFrequent(client.getFrequent());
        r.setStatus(client.getStatus().name());
        r.setCreatedAt(client.getCreatedAt());

        populateLoyaltyProgress(r, client);

        return r;
    }
}
