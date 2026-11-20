package com.loleague.auth.web;

import com.loleague.auth.token.AuthCookies;
import com.loleague.auth.token.TokenService;
import com.loleague.auth.user.User;
import com.loleague.auth.user.UserDtos.*;
import com.loleague.auth.user.UserService;
import com.loleague.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;
    private final AuthCookies cookies;

    public UserController(UserService userService, TokenService tokenService, AuthCookies cookies) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.cookies = cookies;
    }

    @GetMapping("/me")
    public UserResponse me(CurrentUser me) {
        return userService.toResponse(userService.get(me.id()));
    }

    /** Display name, bio, avatar and birthday only. Username and college are permanent. */
    @PatchMapping("/me")
    public UserResponse update(CurrentUser me, @Valid @RequestBody UpdateProfileRequest req) {
        return userService.toResponse(userService.updateProfile(me.id(), req));
    }

    /** Changing the password logs out every other device and gives this one fresh cookies. */
    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(CurrentUser me, @Valid @RequestBody ChangePasswordRequest req,
                                               HttpServletRequest request, HttpServletResponse response) {
        User user = userService.changePassword(me.id(), req);
        tokenService.revokeAll(user.getId());
        cookies.write(response, tokenService.issueForLogin(user, request.getHeader(HttpHeaders.USER_AGENT)));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{username}")
    public PublicProfile profile(CurrentUser me, @PathVariable String username) {
        return userService.publicProfile(username, me.collegeId());
    }
}
