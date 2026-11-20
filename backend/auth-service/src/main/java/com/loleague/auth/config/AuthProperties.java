package com.loleague.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "app")
public record AuthProperties(Jwt jwt, Cookies cookies, Internal internal, String adminUsernames) {

    public record Jwt(String secret, String issuer, Duration accessTtl, Duration refreshTtl) {
    }

    public record Cookies(boolean secure, String sameSite) {
    }

    public record Internal(String memeServiceUrl, String apiKey) {
    }

    /** Comma separated usernames that get the ADMIN role (manage ads, goodie deliveries). */
    public Set<String> admins() {
        if (adminUsernames == null || adminUsernames.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(adminUsernames.split(","))
                .map(String::trim).map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
