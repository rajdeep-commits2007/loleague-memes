package com.loleague.auth.user;

import com.loleague.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny in-memory brute-force guard: 8 failed logins per username+IP => locked for 10 minutes.
 * Good enough for a single auth instance; move to Redis if you scale auth horizontally.
 */
@Component
public class LoginThrottle {

    private static final int MAX_FAILURES = 8;
    private static final long LOCK_SECONDS = 600;

    private record Attempts(int failures, Instant firstFailure) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public void check(String username, String ip) {
        Attempts a = attempts.get(key(username, ip));
        if (a != null && a.failures() >= MAX_FAILURES
                && a.firstFailure().plusSeconds(LOCK_SECONDS).isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Take a chai break and try in 10 minutes.");
        }
    }

    public void failed(String username, String ip) {
        attempts.merge(key(username, ip), new Attempts(1, Instant.now()), (old, n) ->
                old.firstFailure().plusSeconds(LOCK_SECONDS).isBefore(Instant.now())
                        ? n
                        : new Attempts(old.failures() + 1, old.firstFailure()));
    }

    public void succeeded(String username, String ip) {
        attempts.remove(key(username, ip));
    }

    @Scheduled(fixedDelay = 900_000)
    void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(LOCK_SECONDS);
        attempts.entrySet().removeIf(e -> e.getValue().firstFailure().isBefore(cutoff));
    }

    private static String key(String username, String ip) {
        return username.toLowerCase() + "|" + ip;
    }
}
