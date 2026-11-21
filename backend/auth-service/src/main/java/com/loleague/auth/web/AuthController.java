package com.loleague.auth.web;

import com.loleague.auth.clerk.ClerkClient;
import com.loleague.auth.clerk.ClerkClient.ClerkIdentity;
import com.loleague.auth.college.CollegeCatalog;
import com.loleague.auth.token.AuthCookies;
import com.loleague.auth.token.TokenService;
import com.loleague.auth.user.LoginThrottle;
import com.loleague.auth.user.User;
import com.loleague.auth.user.UserDtos.*;
import com.loleague.auth.user.UserService;
import com.loleague.common.security.CookieBearerTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/**
 * Sign-in is done by Clerk (email OTP, Google, GitHub). The browser hands us a short-lived Clerk session token,
 * we verify it, read the verified email from Clerk, and issue our own HttpOnly cookies so the other services
 * keep working exactly as before.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CollegeCatalog colleges;
    private final UserService userService;
    private final TokenService tokenService;
    private final AuthCookies cookies;
    private final LoginThrottle throttle;
    private final ClerkClient clerk;

    public AuthController(CollegeCatalog colleges, UserService userService, TokenService tokenService,
                          AuthCookies cookies, LoginThrottle throttle, ClerkClient clerk) {
        this.colleges = colleges;
        this.userService = userService;
        this.tokenService = tokenService;
        this.cookies = cookies;
        this.throttle = throttle;
        this.clerk = clerk;
    }

    /** Powers the college dropdown on sign-up. */
    @GetMapping("/colleges")
    public ResponseEntity<List<CollegeCatalog.College>> colleges() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(colleges.all());
    }

    /** Called (debounced) while the student types a username. */
    @GetMapping("/username-available")
    public UsernameAvailability usernameAvailable(@RequestParam String username) {
        return userService.checkUsername(username);
    }

    /**
     * Step 1 after every Clerk sign-in. Signs in if this Clerk user already has an account; otherwise says
     * what is missing (a verified email, or the college + username onboarding).
     */
    @PostMapping("/clerk/session")
    public SessionResult session(@Valid @RequestBody ClerkSessionRequest req, HttpServletRequest request,
                                 HttpServletResponse response) {
        ClerkIdentity identity = clerk.identify(req.token());
        var existing = userService.findByClerk(identity.clerkUserId());
        if (existing.isPresent()) {
            return signIn(existing.get(), request, response);
        }
        if (identity.email() == null) {
            return new SessionResult("EMAIL_REQUIRED", null, null, null);
        }
        userService.assertEmailFree(identity.email(), identity.clerkUserId());
        return new SessionResult("ONBOARDING", null, identity.email(), suggestedName(identity));
    }

    /** New student: college (permanent) + unique username. */
    @PostMapping("/clerk/onboard")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResult onboard(@Valid @RequestBody OnboardRequest req, HttpServletRequest request,
                                 HttpServletResponse response) {
        ClerkIdentity identity = clerk.identify(req.token());
        User user = userService.register(identity, req.collegeId(), req.username());
        return signIn(user, request, response);
    }

    /** Student from before Clerk: proves the old account once with its password, then it's linked for good. */
    @PostMapping("/clerk/claim")
    public SessionResult claim(@Valid @RequestBody ClaimRequest req, HttpServletRequest request,
                               HttpServletResponse response) {
        ClerkIdentity identity = clerk.identify(req.token());
        String ip = request.getRemoteAddr();
        throttle.check(req.username(), ip);
        User user;
        try {
            user = userService.claimLegacy(identity, req.username(), req.password());
        } catch (RuntimeException e) {
            throttle.failed(req.username(), ip);
            throw e;
        }
        throttle.succeeded(req.username(), ip);
        return signIn(user, request, response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(@CookieValue(name = CookieBearerTokenResolver.REFRESH_COOKIE, required = false) String refresh,
                                        HttpServletRequest request, HttpServletResponse response) {
        try {
            cookies.write(response, tokenService.rotate(refresh, request.getHeader(HttpHeaders.USER_AGENT)));
        } catch (com.loleague.common.web.ApiException e) {
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) {
                cookies.clear(response);
            }
            throw e;
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = CookieBearerTokenResolver.REFRESH_COOKIE, required = false) String refresh,
                                       HttpServletResponse response) {
        tokenService.revoke(refresh);
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    private SessionResult signIn(User user, HttpServletRequest request, HttpServletResponse response) {
        user = userService.syncRole(user);
        cookies.write(response, tokenService.issueForLogin(user, request.getHeader(HttpHeaders.USER_AGENT)));
        return SessionResult.signedIn(userService.toResponse(user));
    }

    private static String suggestedName(ClerkIdentity identity) {
        String first = identity.firstName() == null ? "" : identity.firstName();
        String last = identity.lastName() == null ? "" : identity.lastName();
        String base = (first + "." + last).toLowerCase().replaceAll("[^a-z0-9.]", "")
                .replaceAll("\\.+", ".").replaceAll("^\\.|\\.$", "");
        return base.length() > 20 ? base.substring(0, 20).replaceAll("\\.$", "") : base;
    }
}
