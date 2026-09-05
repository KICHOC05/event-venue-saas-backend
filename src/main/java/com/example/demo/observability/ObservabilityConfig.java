package com.example.demo.observability;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
public class ObservabilityConfig {

    @Bean
    TimedAspect timedAspect(MeterRegistry meterRegistry) {
        return new TimedAspect(meterRegistry);
    }

    @Bean
    MeterFilter httpRouteCardinalityGuard(
            @Value("${management.metrics.guardrails.max-uri-tags:100}") int maxUriTags) {
        return MeterFilter.maximumAllowableTags(
                "http.server.requests",
                "uri",
                maxUriTags,
                MeterFilter.deny());
    }
}
