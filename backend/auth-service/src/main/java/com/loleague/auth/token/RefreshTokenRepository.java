package com.loleague.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = ?2 where t.familyId = ?1 and t.revokedAt is null")
    int revokeFamily(UUID familyId, Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = ?2 where t.userId = ?1 and t.revokedAt is null")
    int revokeAllForUser(UUID userId, Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < ?1")
    int deleteExpired(Instant before);
}
