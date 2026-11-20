package com.loleague.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** The authenticated student, taken straight from the verified JWT. College and username never change. */
public record CurrentUser(UUID id, String username, String collegeId, String role) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(JwtSupport.CLAIM_USERNAME),
                jwt.getClaimAsString(JwtSupport.CLAIM_COLLEGE),
                jwt.getClaimAsString(JwtSupport.CLAIM_ROLE));
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
