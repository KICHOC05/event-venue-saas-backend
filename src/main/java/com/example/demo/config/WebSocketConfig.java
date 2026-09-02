package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.example.demo.security.JwtService;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.repository.UserRepository;

/**
 * Configuración WebSocket para el sistema de timers.
 * 
 * Endpoint WebSocket: /ws
 * Broker: /topic
 * Prefijo aplicación: /app
 * Autenticación: JWT via ChannelInterceptor en CONNECT frame
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public WebSocketConfig(JwtService jwtService,
            UserRepository userRepository,
            TenantRepository tenantRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new WebSocketAuthInterceptor(
                jwtService, userRepository, tenantRepository));
    }
}