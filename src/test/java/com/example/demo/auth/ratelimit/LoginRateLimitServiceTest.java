package com.example.demo.auth.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginRateLimitServiceTest {

    private static final String CLIENT_ADDRESS = "203.0.113.10";

    private MutableClock clock;
    private LoginRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        rateLimitService = new LoginRateLimitService(
                5,
                Duration.ofMinutes(15),
                Duration.ofMinutes(15),
                clock);
    }

    @Test
    void blocksTheClientOnTheFifthFailedAttempt() {
        for (int attempt = 1; attempt < 5; attempt++) {
            assertDoesNotThrow(() -> rateLimitService.recordFailure(CLIENT_ADDRESS));
        }

        LoginRateLimitExceededException exception = assertThrows(
                LoginRateLimitExceededException.class,
                () -> rateLimitService.recordFailure(CLIENT_ADDRESS));

        assertEquals(900, exception.getRetryAfterSeconds());
        assertThrows(LoginRateLimitExceededException.class,
                () -> rateLimitService.checkAllowed(CLIENT_ADDRESS));
    }

    @Test
    void successfulLoginClearsPreviousFailures() {
        for (int attempt = 0; attempt < 4; attempt++) {
            rateLimitService.recordFailure(CLIENT_ADDRESS);
        }

        rateLimitService.recordSuccess(CLIENT_ADDRESS);

        for (int attempt = 0; attempt < 4; attempt++) {
            assertDoesNotThrow(() -> rateLimitService.recordFailure(CLIENT_ADDRESS));
        }
    }

    @Test
    void expiredWindowStartsANewFailureCounter() {
        for (int attempt = 0; attempt < 4; attempt++) {
            rateLimitService.recordFailure(CLIENT_ADDRESS);
        }

        clock.advance(Duration.ofMinutes(15));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertDoesNotThrow(() -> rateLimitService.recordFailure(CLIENT_ADDRESS));
        }
    }

    @Test
    void clientCanTryAgainAfterBlockExpires() {
        for (int attempt = 0; attempt < 4; attempt++) {
            rateLimitService.recordFailure(CLIENT_ADDRESS);
        }
        assertThrows(LoginRateLimitExceededException.class,
                () -> rateLimitService.recordFailure(CLIENT_ADDRESS));

        clock.advance(Duration.ofMinutes(15));

        assertDoesNotThrow(() -> rateLimitService.checkAllowed(CLIENT_ADDRESS));
        assertDoesNotThrow(() -> rateLimitService.recordFailure(CLIENT_ADDRESS));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
