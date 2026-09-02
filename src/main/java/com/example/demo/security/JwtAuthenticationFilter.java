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

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
            UserRepository userRepository,
            TenantRepository tenantRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
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

        // 🔹 Validar token
        if (!jwtService.isValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

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
                forbidden(response, "Tenant inválido o inactivo");
                return;
            }

            // 🔹 Buscar usuario real
            User user = userRepository.findByPublicId(userPublicId).orElse(null);

            if (user == null || !Boolean.TRUE.equals(user.getActive())) {
                forbidden(response, "Usuario inválido o desactivado");
                return;
            }

            // 🔹 Validar pertenencia al tenant
            if (!user.getTenant().getId().equals(tenantId)) {
                forbidden(response, "Usuario no pertenece al tenant");
                return;
            }

            // 🔹 Validar rol consistente
            if (!user.getRole().equals(roleFromToken)) {
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

            // 🔥 CLAVE: filterChain.doFilter DENTRO del mismo try
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            forbidden(response, "Error de autenticación");
        } finally {
            // 🔹 Limpiar SIEMPRE al final
            TenantContext.clear();
        }
    }

    private void forbidden(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }

}
