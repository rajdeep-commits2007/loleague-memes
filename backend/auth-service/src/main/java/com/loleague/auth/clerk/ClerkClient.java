package com.loleague.auth.clerk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.loleague.auth.config.AuthProperties;
import com.loleague.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Talks to Clerk. Two jobs:
 * <ol>
 *   <li>verify a Clerk session token (RS256, Clerk's JWKS, issuer + azp checked), and</li>
 *   <li>read the Clerk user from the Backend API, because the session token does not carry the phone number
 *       and we only trust a phone Clerk itself marked as verified (OTP done).</li>
 * </ol>
 */
@Component
public class ClerkClient {

    private static final Logger log = LoggerFactory.getLogger(ClerkClient.class);

    /** What we need from a Clerk user. {@code phone} is a verified E.164 number or null. */
    public record ClerkIdentity(String clerkUserId, String phone, String firstName, String lastName, String imageUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClerkUser(String id,
                     @JsonProperty("primary_phone_number_id") String primaryPhoneNumberId,
                     @JsonProperty("phone_numbers") List<PhoneNumber> phoneNumbers,
                     @JsonProperty("first_name") String firstName,
                     @JsonProperty("last_name") String lastName,
                     @JsonProperty("image_url") String imageUrl,
                     Boolean banned,
                     Boolean locked) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PhoneNumber(String id, @JsonProperty("phone_number") String phoneNumber, Verification verification) {
        boolean verified() {
            return verification != null && "verified".equals(verification.status());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Verification(String status) {
    }

    private final JwtDecoder decoder;
    private final RestClient api;
    private final JsonMapper json;

    public ClerkClient(AuthProperties props, JsonMapper json) {
        AuthProperties.Clerk clerk = props.clerk();
        if (clerk == null || isBlank(clerk.issuer()) || isBlank(clerk.secretKey())) {
            throw new IllegalStateException("Set CLERK_ISSUER and CLERK_SECRET_KEY");
        }
        String issuer = clerk.issuer().replaceAll("/+$", "");
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AuthorizedPartyValidator(clerk.parties())));
        this.decoder = nimbus;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.api = RestClient.builder()
                .baseUrl(clerk.apiUrl() == null || clerk.apiUrl().isBlank() ? "https://api.clerk.com/v1" : clerk.apiUrl())
                .defaultHeader("Authorization", "Bearer " + clerk.secretKey())
                .requestFactory(factory)
                .build();
        this.json = json;
    }

    /** Verifies the session token and returns the Clerk user behind it. */
    public ClerkIdentity identify(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in with Clerk first");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(sessionToken);
        } catch (JwtException e) {
            log.debug("Rejected Clerk token: {}", e.getMessage());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Your sign-in expired. Please try again.");
        }
        return fetchUser(jwt.getSubject());
    }

    private ClerkIdentity fetchUser(String clerkUserId) {
        ClerkUser user;
        try {
            String body = api.get().uri("/users/{id}", clerkUserId).retrieve().body(String.class);
            user = json.readValue(body, ClerkUser.class);
        } catch (RestClientResponseException e) {
            log.warn("Clerk Backend API returned {} for {}", e.getStatusCode(), clerkUserId);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Couldn't verify your sign-in. Please try again.");
        } catch (RuntimeException e) {
            log.warn("Clerk Backend API call failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Sign-in is having a moment. Try again shortly.");
        }
        if (Boolean.TRUE.equals(user.banned()) || Boolean.TRUE.equals(user.locked())) {
            throw ApiException.forbidden("This account is blocked");
        }
        return new ClerkIdentity(user.id(), verifiedPhone(user), user.firstName(), user.lastName(), user.imageUrl());
    }

    /** The primary phone if verified, otherwise any verified phone. */
    private static String verifiedPhone(ClerkUser user) {
        List<PhoneNumber> phones = user.phoneNumbers() == null ? List.of() : user.phoneNumbers();
        return phones.stream()
                .filter(PhoneNumber::verified)
                .filter(p -> Objects.equals(p.id(), user.primaryPhoneNumberId()))
                .map(PhoneNumber::phoneNumber)
                .findFirst()
                .or(() -> phones.stream().filter(PhoneNumber::verified).map(PhoneNumber::phoneNumber).findFirst())
                .orElse(null);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Clerk's recommended check: the token's azp must be one of our own origins (when present). */
    private record AuthorizedPartyValidator(Set<String> parties) implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            String azp = token.getClaimAsString("azp");
            if (azp == null || parties.isEmpty() || parties.contains(azp.replaceAll("/+$", ""))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unexpected azp " + azp, null));
        }
    }
}
