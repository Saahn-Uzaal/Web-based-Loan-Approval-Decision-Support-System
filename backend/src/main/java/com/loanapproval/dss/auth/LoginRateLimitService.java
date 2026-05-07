package com.loanapproval.dss.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginRateLimitService {

    private final int maxAttempts;
    private final Duration windowDuration;
    private final Duration lockDuration;
    private final Map<String, FailedAttemptState> failedAttempts = new ConcurrentHashMap<>();

    public LoginRateLimitService(
        @Value("${app.auth.login-rate-limit.max-attempts:5}") int maxAttempts,
        @Value("${app.auth.login-rate-limit.window-minutes:15}") long windowMinutes,
        @Value("${app.auth.login-rate-limit.lock-minutes:15}") long lockMinutes
    ) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowDuration = Duration.ofMinutes(Math.max(1, windowMinutes));
        this.lockDuration = Duration.ofMinutes(Math.max(1, lockMinutes));
    }

    public void assertAllowed(String normalizedEmail, String clientIp) {
        String key = buildKey(normalizedEmail, clientIp);
        FailedAttemptState state = failedAttempts.get(key);
        if (state == null) {
            return;
        }

        Instant now = Instant.now();
        if (state.blockedUntil() != null) {
            if (now.isBefore(state.blockedUntil())) {
                throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã đăng nhập sai quá nhiều lần. Vui lòng thử lại sau ít phút."
                );
            }
            failedAttempts.remove(key, state);
            return;
        }

        if (now.isAfter(state.windowStartedAt().plus(windowDuration))) {
            failedAttempts.remove(key, state);
        }
    }

    public void recordFailure(String normalizedEmail, String clientIp) {
        String key = buildKey(normalizedEmail, clientIp);
        Instant now = Instant.now();

        failedAttempts.compute(key, (ignored, currentState) -> {
            if (currentState == null || now.isAfter(currentState.windowStartedAt().plus(windowDuration))) {
                return new FailedAttemptState(1, now, null);
            }

            int attempts = currentState.attempts() + 1;
            Instant blockedUntil = attempts >= maxAttempts ? now.plus(lockDuration) : null;
            return new FailedAttemptState(attempts, currentState.windowStartedAt(), blockedUntil);
        });
    }

    public void recordSuccess(String normalizedEmail, String clientIp) {
        failedAttempts.remove(buildKey(normalizedEmail, clientIp));
    }

    private String buildKey(String normalizedEmail, String clientIp) {
        return normalizedEmail + "|" + (clientIp != null ? clientIp : "unknown");
    }

    private record FailedAttemptState(
        int attempts,
        Instant windowStartedAt,
        Instant blockedUntil
    ) {
    }
}
