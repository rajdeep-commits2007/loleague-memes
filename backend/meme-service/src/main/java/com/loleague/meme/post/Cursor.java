package com.loleague.meme.post;

import com.loleague.common.web.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/** Opaque keyset cursor: base64url("<createdAt ISO>|<id>"). Stable under inserts, unlike OFFSET paging. */
public record Cursor(OffsetDateTime createdAt, UUID id) {

    public String encode() {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf('|');
            return new Cursor(OffsetDateTime.parse(raw.substring(0, sep)), UUID.fromString(raw.substring(sep + 1)));
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Bad cursor");
        }
    }
}
