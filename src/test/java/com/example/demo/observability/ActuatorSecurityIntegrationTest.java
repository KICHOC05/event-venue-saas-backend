package com.example.demo.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Test
    void healthIsPublicWithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void metricsAndPrometheusRequireAdminRole() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/metrics").with(user("worker").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"));
    }

    @Test
    void nonExposedActuatorEndpointRemainsBlockedForAdmin() throws Exception {
        mockMvc.perform(get("/actuator/env").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void correlationHeaderIsReturnedByActuatorToo() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header(CorrelationIdFilter.HEADER_NAME, "probe-123"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse()
                        .getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("probe-123"));
    }

    @Test
    void hikariPoolMetricsAreBound() throws Exception {
        try (Connection ignored = dataSource.getConnection()) {
            assertThat(ignored.isClosed()).isFalse();
        }

        Set<String> meterNames = meterRegistry.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .collect(Collectors.toSet());

        assertThat(meterNames).contains(
                "hikaricp.connections.active",
                "hikaricp.connections.idle",
                "hikaricp.connections.pending",
                "hikaricp.connections.timeout");
    }

    @Test
    void observabilityConfigurationUsesBoundedHttpTagsAndExpectedPercentiles() {
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,metrics,prometheus");
        assertThat(environment.getProperty("management.metrics.guardrails.max-uri-tags", Integer.class))
                .isBetween(1, 1000);
        assertThat(environment.getProperty("management.metrics.distribution.percentiles.http.server.requests"))
                .isEqualTo("0.50,0.95,0.99");
        assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
    }
}
