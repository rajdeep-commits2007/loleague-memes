package com.loleague.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.util.List;

/**
 * Reads the access token from the HttpOnly cookie (falls back to the Authorization header for tooling).
 * Paths listed in {@code ignoredPrefixes} never resolve a token, so an expired cookie can't block
 * public endpoints such as login / refresh.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    public static final String ACCESS_COOKIE = "ll_access";
    public static final String REFRESH_COOKIE = "ll_refresh";
    public static final String SESSION_HINT_COOKIE = "ll_session";

    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
    private final List<String> ignoredPrefixes;

    public CookieBearerTokenResolver(List<String> ignoredPrefixes) {
        this.ignoredPrefixes = ignoredPrefixes;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : ignoredPrefixes) {
            if (path.startsWith(prefix)) {
                return null;
            }
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return headerResolver.resolve(request);
    }
}
