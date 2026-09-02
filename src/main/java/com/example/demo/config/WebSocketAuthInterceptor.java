package com.example.demo.config;

import com.example.demo.common.enums.TenantStatus;
import com.example.demo.common.enums.UserRole;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.security.JwtService;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.List;

public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public WebSocketAuthInterceptor(JwtService jwtService,
            UserRepository userRepository,
            TenantRepository tenantRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        switch (command) {
            case CONNECT:
                return handleConnect(message, accessor);
            case SUBSCRIBE:
                return handleSubscribe(message, accessor);
            case DISCONNECT:
                TenantContext.clear();
                return message;
            default:
                return message;
        }
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            throw new IllegalArgumentException("Token JWT requerido");
        }

        String authHeader = authHeaders.get(0);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Formato de token inválido");
        }

        String token = authHeader.substring(7);

        if (!jwtService.isValid(token)) {
            throw new IllegalArgumentException("Token JWT inválido o expirado");
        }

        String userPublicId = jwtService.getUserPublicId(token);
        Long tenantId = jwtService.getTenantId(token);
        Long branchId = jwtService.getBranchId(token);
        UserRole roleFromToken = jwtService.getRole(token);

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null ||
                tenant.getStatus() == TenantStatus.SUSPENDED ||
                tenant.getStatus() == TenantStatus.CANCELLED) {
            throw new IllegalArgumentException("Tenant inválido o inactivo");
        }

        User user = userRepository.findByPublicId(userPublicId).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("Usuario inválido o desactivado");
        }

        if (!user.getTenant().getId().equals(tenantId)) {
            throw new IllegalArgumentException("Usuario no pertenece al tenant");
        }

        if (!user.getRole().equals(roleFromToken)) {
            throw new IllegalArgumentException("Rol inconsistente en el token");
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        accessor.setUser(auth);

        accessor.setNativeHeader("tenantId", String.valueOf(tenantId));
        accessor.setNativeHeader("branchId", String.valueOf(branchId));

        TenantContext.set(new TenantContext.TenantInfo(
                tenantId, branchId, user.getId(), user.getRole()));

        return message;
    }

    private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (!(principal instanceof Authentication auth)
                || !(auth.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new IllegalArgumentException("No autenticado");
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            throw new IllegalArgumentException("Destino no especificado");
        }

        Long userTenantId = userDetails.getTenantId();
        if (userTenantId == null) {
            throw new IllegalArgumentException("Usuario sin tenant asignado");
        }

        String expectedPrefix = "/topic/tenant/" + userTenantId + "/";
        if (!destination.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException(
                    "Acceso denegado al topic: " + destination);
        }

        return message;
    }
}
