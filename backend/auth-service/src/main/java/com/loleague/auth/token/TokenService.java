package com.loleague.auth.token;

import com.loleague.auth.config.AuthProperties;
import com.loleague.auth.user.User;
import com.loleague.auth.user.UserRepository;
import com.loleague.common.security.JwtSupport;
import com.loleague.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Short-lived access JWT (15 min) + long-lived opaque refresh token (30 days) stored hashed.
 * Refresh tokens rotate on every use; replaying an already-used token revokes the whole login family.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long REUSE_GRACE_SECONDS = 20;

    public record IssuedTokens(String accessToken, String refreshToken) {
    }

    private final AuthProperties props;
    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;

    public TokenService(AuthProperties props, RefreshTokenRepository refreshTokens, UserRepository users) {
        this.props = props;
        this.encoder = JwtSupport.encoder(props.jwt().secret());
        this.refreshTokens = refreshTokens;
        this.users = users;
    }

    @Transactional
    public IssuedTokens issueForLogin(User user, String userAgent) {
        return issue(user, UUID.randomUUID(), userAgent);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public IssuedTokens rotate(String rawRefreshToken, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again.");
        }
        RefreshToken current = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again."));

        if (current.getRevokedAt() != null
                && current.getRevokedAt().isAfter(Instant.now().minusSeconds(REUSE_GRACE_SECONDS))) {
            // Two tabs refreshed at the same moment. The other tab already got fresh cookies; tell the client to retry.
            throw new ApiException(HttpStatus.CONFLICT, "Session was just refreshed. Retry.");
        }
        if (current.getRevokedAt() != null) {
            // Someone is replaying an old token -> treat the whole family as stolen.
            log.warn("Refresh token reuse detected for user {}", current.getUserId());
            refreshTokens.revokeFamily(current.getFamilyId(), Instant.now());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again.");
        }
        if (!current.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again.");
        }
        current.revoke();
        User user = users.findById(current.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));
        return issue(user, current.getFamilyId(), userAgent);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.jwt().accessTtl()))
                .claim(JwtSupport.CLAIM_USERNAME, user.getUsername())
                .claim(JwtSupport.CLAIM_COLLEGE, user.getCollegeId())
                .claim(JwtSupport.CLAIM_ROLE, user.getRole())
                .claim(JwtSupport.CLAIM_TYPE, JwtSupport.TYPE_ACCESS)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private IssuedTokens issue(User user, UUID familyId, String userAgent) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.getId(), familyId, hash(raw),
                Instant.now().plus(props.jwt().refreshTtl()), userAgent));
        return new IssuedTokens(createAccessToken(user), raw);
    }

    /** Nightly housekeeping. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void purgeExpired() {
        int removed = refreshTokens.deleteExpired(Instant.now());
        log.info("Purged {} expired refresh tokens", removed);
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
