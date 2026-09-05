package com.example.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.demo.branch.model.Branch;
import com.example.demo.common.enums.TenantStatus;
import com.example.demo.common.enums.UserRole;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterMetricsTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            jwtService, userRepository, tenantRepository, meterRegistry);

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        meterRegistry.clear();
    }

    @Test
    void recordsOnlyJwtAuthenticationWorkBeforeTheApplicationChain() throws Exception {
        String token = "valid-test-token";
        Tenant tenant = mock(Tenant.class);
        Branch branch = mock(Branch.class);
        User user = mock(User.class);

        when(jwtService.isValid(token)).thenReturn(true);
        when(jwtService.getUserPublicId(token)).thenReturn("user-public-id");
        when(jwtService.getTenantId(token)).thenReturn(10L);
        when(jwtService.getBranchId(token)).thenReturn(20L);
        when(jwtService.getRole(token)).thenReturn(UserRole.CASHIER);
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(tenant.getStatus()).thenReturn(TenantStatus.ACTIVE);
        when(userRepository.findByPublicId("user-public-id")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(30L);
        when(user.getPublicId()).thenReturn("user-public-id");
        when(user.getEmail()).thenReturn("cashier@example.test");
        when(user.getPassword()).thenReturn("not-used-by-this-test");
        when(user.getActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.CASHIER);
        when(user.getTenant()).thenReturn(tenant);
        when(user.getBranch()).thenReturn(branch);
        when(tenant.getId()).thenReturn(10L);
        when(branch.getId()).thenReturn(20L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean applicationChainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response,
                (servletRequest, servletResponse) -> applicationChainCalled.set(true));

        assertThat(applicationChainCalled).isTrue();
        assertThat(meterRegistry.get("spacekids.security.jwt.authentication")
                .tag("outcome", "authenticated").timer().count()).isEqualTo(1);
    }

    @Test
    void recordsInvalidTokenWithBoundedOutcomeTag() throws Exception {
        when(jwtService.isValid("invalid-test-token")).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-test-token");

        filter.doFilter(request, new MockHttpServletResponse(),
                (servletRequest, servletResponse) -> { });

        assertThat(meterRegistry.get("spacekids.security.jwt.authentication")
                .tag("outcome", "invalid-token").timer().count()).isEqualTo(1);
    }
}
