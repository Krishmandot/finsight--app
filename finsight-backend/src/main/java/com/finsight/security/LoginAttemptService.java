package com.finsight.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Throttles password guessing per account. After {@code maxFailures} failed attempts within the window,
 * further attempts for that email are refused until the lockout expires. State is in-memory, which is
 * sufficient for a single application instance; a multi-instance deployment should back this with a
 * shared store.
 */
@Service
public class LoginAttemptService {

    private final int maxFailures;
    private final Duration lockout;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(
        @Value("${finsight.security.login.max-failures:5}") int maxFailures,
        @Value("${finsight.security.login.lockout-minutes:15}") long lockoutMinutes
    ) {
        this(maxFailures, Duration.ofMinutes(lockoutMinutes), Clock.systemUTC());
    }

    LoginAttemptService(int maxFailures, Duration lockout, Clock clock) {
        this.maxFailures = maxFailures;
        this.lockout = lockout;
        this.clock = clock;
    }

    public boolean isBlocked(String email) {
        Attempts entry = attempts.get(key(email));
        if (entry == null) {
            return false;
        }
        if (entry.windowStart.plus(lockout).isBefore(clock.instant())) {
            attempts.remove(key(email));
            return false;
        }
        return entry.failures >= maxFailures;
    }

    public void recordFailure(String email) {
        Instant now = clock.instant();
        attempts.compute(key(email), (k, existing) -> {
            if (existing == null || existing.windowStart.plus(lockout).isBefore(now)) {
                return new Attempts(1, now);
            }
            return new Attempts(existing.failures + 1, existing.windowStart);
        });
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempts(int failures, Instant windowStart) {
    }
}
