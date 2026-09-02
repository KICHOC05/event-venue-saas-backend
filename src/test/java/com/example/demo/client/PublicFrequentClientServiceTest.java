package com.example.demo.client;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.client.dto.PublicFrequentClientRegistrationRequest;
import com.example.demo.client.dto.PublicFrequentClientRegistrationResponse;
import com.example.demo.client.model.Client;
import com.example.demo.client.model.ClientStatus;
import com.example.demo.client.repository.ClientRepository;
import com.example.demo.client.service.PublicFrequentClientService;
import com.example.demo.common.enums.TenantStatus;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicFrequentClientServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ClientRepository clientRepository;

    @InjectMocks private PublicFrequentClientService publicFrequentClientService;

    @Test
    void createsAnActiveFrequentClientInThePrimaryBranch() {
        Tenant tenant = activeTenant();
        Branch branch = new Branch();
        branch.setId(20L);
        branch.setTenant(tenant);
        branch.setName("Space Kids");

        when(tenantRepository.findByPublicIdAndStatus("tenant-public-id", TenantStatus.ACTIVE))
                .thenReturn(Optional.of(tenant));
        when(clientRepository.findFirstByTenant_IdAndPhoneOrderByIdAsc(10L, "5512345678"))
                .thenReturn(Optional.empty());
        when(branchRepository.findFirstByTenant_IdOrderByIdAsc(10L))
                .thenReturn(Optional.of(branch));

        PublicFrequentClientRegistrationResponse response = publicFrequentClientService.register(
                " tenant-public-id ", request("María López", "Emilio", "55 1234 5678", "MARIA@EXAMPLE.COM"));

        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(clientCaptor.capture());
        Client saved = clientCaptor.getValue();

        assertEquals(tenant, saved.getTenant());
        assertEquals(branch, saved.getBranch());
        assertEquals("María López", saved.getParentName());
        assertEquals("Emilio", saved.getChildName());
        assertEquals("5512345678", saved.getPhone());
        assertEquals("maria@example.com", saved.getEmail());
        assertTrue(saved.getFrequent());
        assertEquals(ClientStatus.ACTIVE, saved.getStatus());
        assertNotNull(saved.getFrequentProgramConsentAt());
        assertEquals("ACTIVE", response.getStatus());
        assertFalse(response.isPhoneVerificationRequired());
    }

    @Test
    void reusesAnExistingClientWithoutOverwritingKnownIdentityData() {
        Tenant tenant = activeTenant();
        Client existing = new Client();
        existing.setId(30L);
        existing.setTenant(tenant);
        existing.setParentName("Nombre existente");
        existing.setChildName("Mateo");
        existing.setEmail("existente@example.com");
        existing.setPhone("5512345678");
        existing.setFrequent(false);
        existing.setStatus(ClientStatus.INACTIVE);

        when(tenantRepository.findByPublicIdAndStatus("tenant-public-id", TenantStatus.ACTIVE))
                .thenReturn(Optional.of(tenant));
        when(clientRepository.findFirstByTenant_IdAndPhoneOrderByIdAsc(10L, "5512345678"))
                .thenReturn(Optional.of(existing));

        publicFrequentClientService.register(
                "tenant-public-id", request("Otro nombre", "Sofía", "5512345678", "otro@example.com"));

        assertEquals("Nombre existente", existing.getParentName());
        assertEquals("Mateo", existing.getChildName());
        assertEquals("existente@example.com", existing.getEmail());
        assertTrue(existing.getFrequent());
        assertEquals(ClientStatus.ACTIVE, existing.getStatus());
        assertNotNull(existing.getFrequentProgramConsentAt());
        verify(branchRepository, never()).findFirstByTenant_IdOrderByIdAsc(10L);
        verify(clientRepository).save(existing);
    }

    @Test
    void completesTheMissingChildNameWhenThePhoneAlreadyExists() {
        Tenant tenant = activeTenant();
        Client existing = new Client();
        existing.setId(30L);
        existing.setTenant(tenant);
        existing.setParentName("María López");
        existing.setPhone("5512345678");

        when(tenantRepository.findByPublicIdAndStatus("tenant-public-id", TenantStatus.ACTIVE))
                .thenReturn(Optional.of(tenant));
        when(clientRepository.findFirstByTenant_IdAndPhoneOrderByIdAsc(10L, "5512345678"))
                .thenReturn(Optional.of(existing));

        publicFrequentClientService.register(
                "tenant-public-id", request("María López", "Emilio", "5512345678", null));

        assertEquals("Emilio", existing.getChildName());
        verify(clientRepository).save(existing);
    }

    @Test
    void rejectsPhonesOutsideTheSmsCompatibleLength() {
        Tenant tenant = activeTenant();
        when(tenantRepository.findByPublicIdAndStatus("tenant-public-id", TenantStatus.ACTIVE))
                .thenReturn(Optional.of(tenant));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> publicFrequentClientService.register(
                        "tenant-public-id", request("María", "Emilio", "123-45", null)));

        assertEquals("Ingresa un teléfono válido de 10 a 15 dígitos", error.getMessage());
        verify(clientRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Tenant activeTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setPublicId("tenant-public-id");
        tenant.setBusinessName("Space Kids");
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenant;
    }

    private PublicFrequentClientRegistrationRequest request(
            String parentName,
            String childName,
            String phone,
            String email) {
        PublicFrequentClientRegistrationRequest request = new PublicFrequentClientRegistrationRequest();
        request.setParentName(parentName);
        request.setChildName(childName);
        request.setPhone(phone);
        request.setEmail(email);
        request.setConsentAccepted(true);
        return request;
    }
}
