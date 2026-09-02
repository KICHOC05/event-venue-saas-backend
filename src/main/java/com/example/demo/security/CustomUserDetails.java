package com.example.demo.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.demo.user.model.User;
import com.example.demo.common.enums.UserRole;

import lombok.Getter;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;              // ID interno
    private final String publicId;      // UUID público
    private final String email;
    private final String password;

    private final Long tenantId;
    private final Long branchId;

    private final UserRole role;
    private final boolean active;

    public CustomUserDetails(User user) {
        Objects.requireNonNull(user, "User no puede ser null");

        this.id = user.getId();
        this.publicId = user.getPublicId();
        this.email = user.getEmail();
        this.password = user.getPassword();

        // ⚠ Evitar LazyInitializationException
        this.tenantId = user.getTenant().getId();
        this.branchId = user.getBranch().getId();

        this.role = user.getRole();
        this.active = Boolean.TRUE.equals(user.getActive());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_" + role.name())
        );
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    // 🔥 Opcional pero profesional
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomUserDetails that)) return false;
        return Objects.equals(publicId, that.publicId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicId);
    }
}