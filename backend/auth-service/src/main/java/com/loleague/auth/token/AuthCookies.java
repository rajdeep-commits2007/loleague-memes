package com.loleague.auth.token;

import com.loleague.auth.config.AuthProperties;
import com.loleague.common.security.CookieBearerTokenResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * ll_access  - HttpOnly JWT, path "/", 15 min
 * ll_refresh - HttpOnly opaque token, path "/api/auth" (only ever sent to refresh/logout), 30 days
 * ll_session - readable "you are probably logged in" hint for the Next.js route guard. Holds no secret.
 */
@Component
public class AuthCookies {

    private static final String REFRESH_PATH = "/api/auth";

    private final AuthProperties props;

    public AuthCookies(AuthProperties props) {
        this.props = props;
    }

    public void write(HttpServletResponse response, TokenService.IssuedTokens tokens) {
        add(response, cookie(CookieBearerTokenResolver.ACCESS_COOKIE, tokens.accessToken(), "/", props.jwt().accessTtl(), true));
        add(response, cookie(CookieBearerTokenResolver.REFRESH_COOKIE, tokens.refreshToken(), REFRESH_PATH, props.jwt().refreshTtl(), true));
        add(response, cookie(CookieBearerTokenResolver.SESSION_HINT_COOKIE, "1", "/", props.jwt().refreshTtl(), false));
    }

    public void clear(HttpServletResponse response) {
        add(response, cookie(CookieBearerTokenResolver.ACCESS_COOKIE, "", "/", Duration.ZERO, true));
        add(response, cookie(CookieBearerTokenResolver.REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO, true));
        add(response, cookie(CookieBearerTokenResolver.SESSION_HINT_COOKIE, "", "/", Duration.ZERO, false));
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge, boolean httpOnly) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(props.cookies().secure())
                .sameSite(props.cookies().sameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
