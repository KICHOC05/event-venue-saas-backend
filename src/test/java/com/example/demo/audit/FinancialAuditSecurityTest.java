package com.example.demo.audit;

import com.example.demo.audit.controller.FinancialAuditController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialAuditSecurityTest {

    @Test
    void auditEndpointIsRestrictedToAdminAndManager() {
        PreAuthorize annotation = Arrays.stream(FinancialAuditController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("getAudit"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(PreAuthorize.class);

        assertEquals("hasAnyRole('ADMIN','MANAGER')", annotation.value());
    }
}
