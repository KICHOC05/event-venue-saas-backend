package com.example.demo.event.migration;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.event-number-migration.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class EventNumberMigrationRunner implements ApplicationRunner {

    private final EventNumberMigrationService migrationService;

    @Override
    public void run(ApplicationArguments args) {
        migrationService.migrateHistoricalEvents();
    }
}
