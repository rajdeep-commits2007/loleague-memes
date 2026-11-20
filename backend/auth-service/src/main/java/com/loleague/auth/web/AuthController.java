package com.loleague.auth.web;

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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CollegeCatalog colleges;
    private final UserService userService;
    private final TokenService tokenService;
    private final AuthCookies cookies;
    private final LoginThrottle throttle;

    public AuthController(CollegeCatalog colleges, UserService userService, TokenService tokenService,
                          AuthCookies cookies, LoginThrottle throttle) {
        this.colleges = colleges;
        this.userService = userService;
        this.tokenService = tokenService;
        this.cookies = cookies;
        this.throttle = throttle;
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

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest request,
                                 HttpServletResponse response) {
        User user = userService.register(req);
        cookies.write(response, tokenService.issueForLogin(user, request.getHeader(HttpHeaders.USER_AGENT)));
        return userService.toResponse(user);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request,
                              HttpServletResponse response) {
        String ip = request.getRemoteAddr();
        throttle.check(req.username(), ip);
        User user;
        try {
            user = userService.authenticate(req);
        } catch (RuntimeException e) {
            throttle.failed(req.username(), ip);
            throw e;
        }
        throttle.succeeded(req.username(), ip);
        user = userService.syncRole(user);
        cookies.write(response, tokenService.issueForLogin(user, request.getHeader(HttpHeaders.USER_AGENT)));
        return userService.toResponse(user);
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
}
