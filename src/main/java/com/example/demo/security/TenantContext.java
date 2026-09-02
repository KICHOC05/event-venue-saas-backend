package com.example.demo.security;

import com.example.demo.common.enums.UserRole;
import lombok.Getter;

public final class TenantContext {

    private static final ThreadLocal<TenantInfo> CONTEXT = new ThreadLocal<>();

    private TenantContext() {
        // Evita instanciación
    }

    public static void set(TenantInfo info) {
        CONTEXT.set(info);
    }

    public static TenantInfo get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static Long getTenantId() {
        return requireContext().getTenantId();
    }

    public static Long getBranchId() {
        return requireContext().getBranchId();
    }

    public static Long getUserId() {
        // Return null instead of throwing if context not set
        TenantInfo info = CONTEXT.get();
        return info != null ? info.getUserId() : null;
    }

    public static UserRole getRole() {
        return requireContext().getRole();
    }

    private static TenantInfo requireContext() {
        TenantInfo info = CONTEXT.get();
        if (info == null) {
            throw new IllegalStateException("TenantContext no inicializado en este hilo");
        }
        return info;
    }

    @Getter
    public static class TenantInfo {

        private final Long tenantId;
        private final Long branchId;
        private final Long userId;
        private final UserRole role;

        public TenantInfo(Long tenantId,
                Long branchId,
                Long userId,
                UserRole role) {

            this.tenantId = tenantId;
            this.branchId = branchId;
            this.userId = userId;
            this.role = role;
        }
    }
}