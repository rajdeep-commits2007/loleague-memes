package com.loleague.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "app")
public record AuthProperties(Jwt jwt, Cookies cookies, Internal internal, Clerk clerk, String adminUsernames) {

    public record Jwt(String secret, String issuer, Duration accessTtl, Duration refreshTtl) {
    }

    public record Cookies(boolean secure, String sameSite) {
    }

    public record Internal(String memeServiceUrl, String chatServiceUrl, String apiKey) {
    }

    /**
     * @param issuer            your Clerk Frontend API URL, e.g. https://clerk.loleague.in or https://xyz.clerk.accounts.dev
     * @param secretKey         sk_live_... / sk_test_... (Backend API, used to read the verified email address)
     * @param authorizedParties comma separated origins allowed in the token's azp claim (your web app URLs)
     */
    public record Clerk(String issuer, String secretKey, String authorizedParties, String apiUrl) {
        public Set<String> parties() {
            if (authorizedParties == null || authorizedParties.isBlank()) {
                return Set.of();
            }
            return Arrays.stream(authorizedParties.split(","))
                    .map(String::trim).map(s -> s.replaceAll("/+$", ""))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
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
