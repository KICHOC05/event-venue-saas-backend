package com.example.demo.auth.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoginRateLimitService {

    private static final long CLEANUP_INTERVAL = 256;

    private final int maxAttempts;
    private final Duration window;
    private final Duration blockDuration;
    private final Clock clock;
    private final ConcurrentHashMap<String, AttemptState> attemptsByClient = new ConcurrentHashMap<>();
    private final AtomicLong operationCount = new AtomicLong();

    @Autowired
    public LoginRateLimitService(
            @Value("${app.login-rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${app.login-rate-limit.window-seconds:900}") long windowSeconds,
            @Value("${app.login-rate-limit.block-seconds:900}") long blockSeconds) {
        this(maxAttempts, Duration.ofSeconds(windowSeconds), Duration.ofSeconds(blockSeconds), Clock.systemUTC());
    }

    LoginRateLimitService(int maxAttempts, Duration window, Duration blockDuration, Clock clock) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Login rate-limit max attempts must be at least 1");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Login rate-limit window must be positive");
        }
        if (blockDuration == null || blockDuration.isZero() || blockDuration.isNegative()) {
            throw new IllegalArgumentException("Login rate-limit block duration must be positive");
        }

        this.maxAttempts = maxAttempts;
        this.window = window;
        this.blockDuration = blockDuration;
        this.clock = clock;
    }

    public void checkAllowed(String clientAddress) {
        String clientKey = normalizeClientAddress(clientAddress);
        Instant now = clock.instant();
        AtomicLong retryAfterSeconds = new AtomicLong();

        attemptsByClient.computeIfPresent(clientKey, (key, state) -> {
            if (state.isBlockedAt(now)) {
                retryAfterSeconds.set(secondsUntil(now, state.blockedUntil()));
                return state;
            }

            if (state.isExpiredAt(now, window)) {
                return null;
            }

            return state;
        });

        cleanupExpiredEntriesIfNeeded(now);

        if (retryAfterSeconds.get() > 0) {
            throw new LoginRateLimitExceededException(retryAfterSeconds.get());
        }
    }

    public void recordFailure(String clientAddress) {
        String clientKey = normalizeClientAddress(clientAddress);
        Instant now = clock.instant();
        AtomicLong retryAfterSeconds = new AtomicLong();

        attemptsByClient.compute(clientKey, (key, current) -> {
            if (current != null && current.isBlockedAt(now)) {
                retryAfterSeconds.set(secondsUntil(now, current.blockedUntil()));
                return current;
            }

            AttemptState active = current;
            if (active == null || active.isExpiredAt(now, window)) {
                active = new AttemptState(0, now, null);
            }

            int failedAttempts = active.failedAttempts() + 1;
            if (failedAttempts >= maxAttempts) {
                Instant blockedUntil = now.plus(blockDuration);
                retryAfterSeconds.set(secondsUntil(now, blockedUntil));
                return new AttemptState(failedAttempts, active.windowStartedAt(), blockedUntil);
            }

            return new AttemptState(failedAttempts, active.windowStartedAt(), null);
        });

        cleanupExpiredEntriesIfNeeded(now);

        if (retryAfterSeconds.get() > 0) {
            throw new LoginRateLimitExceededException(retryAfterSeconds.get());
        }
    }

    public void recordSuccess(String clientAddress) {
        attemptsByClient.remove(normalizeClientAddress(clientAddress));
    }

    private String normalizeClientAddress(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) {
            return "unknown";
        }
        return clientAddress.trim();
    }

    private void cleanupExpiredEntriesIfNeeded(Instant now) {
        if (operationCount.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }

        attemptsByClient.entrySet().removeIf(entry -> entry.getValue().isExpiredAt(now, window));
    }

    private long secondsUntil(Instant now, Instant deadline) {
        Duration remaining = Duration.between(now, deadline);
        long seconds = remaining.getSeconds();
        return Math.max(1, remaining.getNano() == 0 ? seconds : seconds + 1);
    }

    private record AttemptState(int failedAttempts, Instant windowStartedAt, Instant blockedUntil) {

        private boolean isBlockedAt(Instant now) {
            return blockedUntil != null && now.isBefore(blockedUntil);
        }

        private boolean isExpiredAt(Instant now, Duration window) {
            if (blockedUntil != null) {
                return !now.isBefore(blockedUntil);
            }
            return !now.isBefore(windowStartedAt.plus(window));
        }
    }
}
