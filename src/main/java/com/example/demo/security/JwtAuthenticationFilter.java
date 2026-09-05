package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.demo.common.enums.TenantStatus;
import com.example.demo.common.enums.UserRole;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final MeterRegistry meterRegistry;

    public JwtAuthenticationFilter(JwtService jwtService,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            MeterRegistry meterRegistry) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");

        // 🔹 Si no hay token, continuar sin autenticar
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        long authenticationStartedAt = System.nanoTime();

        // 🔹 Validar token
        if (!jwtService.isValid(token)) {
            recordAuthentication(authenticationStartedAt, "invalid-token");
            filterChain.doFilter(request, response);
            return;
        }

        boolean authenticationRecorded = false;
        try {
            // 🔹 Extraer claims
            String userPublicId = jwtService.getUserPublicId(token);
            Long tenantId = jwtService.getTenantId(token);
            Long branchId = jwtService.getBranchId(token);
            UserRole roleFromToken = jwtService.getRole(token);

            // 🔹 Validar tenant
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);

            if (tenant == null ||
                    tenant.getStatus() == TenantStatus.SUSPENDED ||
                    tenant.getStatus() == TenantStatus.CANCELLED) {
                recordAuthentication(authenticationStartedAt, "tenant-rejected");
                authenticationRecorded = true;
                forbidden(response, "Tenant inválido o inactivo");
                return;
            }

            // 🔹 Buscar usuario real
            User user = userRepository.findByPublicId(userPublicId).orElse(null);

            if (user == null || !Boolean.TRUE.equals(user.getActive())) {
                recordAuthentication(authenticationStartedAt, "user-rejected");
                authenticationRecorded = true;
                forbidden(response, "Usuario inválido o desactivado");
                return;
            }

            // 🔹 Validar pertenencia al tenant
            if (!user.getTenant().getId().equals(tenantId)) {
                recordAuthentication(authenticationStartedAt, "tenant-mismatch");
                authenticationRecorded = true;
                forbidden(response, "Usuario no pertenece al tenant");
                return;
            }

            // 🔹 Validar rol consistente
            if (!user.getRole().equals(roleFromToken)) {
                recordAuthentication(authenticationStartedAt, "role-mismatch");
                authenticationRecorded = true;
                forbidden(response, "Rol inconsistente");
                return;
            }

            // 🔹 Establecer TenantContext
            TenantContext.set(
                    new TenantContext.TenantInfo(
                            tenantId,
                            branchId,
                            user.getId(),
                            user.getRole()));

            // 🔹 Crear autenticación
            CustomUserDetails userDetails = new CustomUserDetails(user);

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities());

            authToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authToken);

            recordAuthentication(authenticationStartedAt, "authenticated");
            authenticationRecorded = true;

            // 🔥 CLAVE: filterChain.doFilter DENTRO del mismo try
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            if (!authenticationRecorded) {
                recordAuthentication(authenticationStartedAt, "error");
            }
            forbidden(response, "Error de autenticación");
        } finally {
            // 🔹 Limpiar SIEMPRE al final
            TenantContext.clear();
        }
    }

    private void recordAuthentication(long startedAt, String outcome) {
        meterRegistry.timer(
                        "spacekids.security.jwt.authentication",
                        "outcome", outcome)
                .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
    }

    private void forbidden(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }

}
