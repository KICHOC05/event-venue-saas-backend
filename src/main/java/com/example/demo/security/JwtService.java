package com.example.demo.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.common.enums.UserRole;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(
            String userPublicId,
            String email,
            Long tenantId,
            Long branchId,
            UserRole role) {
        return Jwts.builder()
                .subject(email)
                .claims(Map.of(
                        "userPublicId", userPublicId,
                        "tenantId", tenantId,
                        "branchId", branchId,
                        "role", role.name()))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getKey())
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getEmail(String token) {
        return extractClaims(token).getSubject();
    }

    // ✅ FIX: Usar Number en lugar de Long.class
    public Long getTenantId(String token) {
        Object value = extractClaims(token).get("tenantId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("tenantId no es un número válido en el token");
    }

    // ✅ FIX: Usar Number en lugar de Long.class
    public Long getBranchId(String token) {
        Object value = extractClaims(token).get("branchId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("branchId no es un número válido en el token");
    }

    public String getUserPublicId(String token) {
        return extractClaims(token).get("userPublicId", String.class);
    }

    public UserRole getRole(String token) {
        String role = extractClaims(token).get("role", String.class);
        return UserRole.valueOf(role);
    }
}