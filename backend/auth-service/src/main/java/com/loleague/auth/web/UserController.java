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

    /** Display name, bio, avatar and birthday. Username has its own endpoint; college is permanent. */
    @PatchMapping("/me")
    public UserResponse update(CurrentUser me, @Valid @RequestBody UpdateProfileRequest req) {
        return userService.toResponse(userService.updateProfile(me.id(), req));
    }

    /**
     * Usernames are unique but changeable (college is not). The access token carries the username, so this
     * device gets fresh cookies right away; other devices pick it up on their next refresh.
     */
    @PatchMapping("/me/username")
    public UserResponse changeUsername(CurrentUser me, @Valid @RequestBody ChangeUsernameRequest req,
                                       HttpServletRequest request, HttpServletResponse response) {
        User user = userService.changeUsername(me.id(), req.username());
        cookies.write(response, tokenService.issueForLogin(user, request.getHeader(HttpHeaders.USER_AGENT)));
        return userService.toResponse(user);
    }

    @GetMapping("/{username}")
    public PublicProfile profile(CurrentUser me, @PathVariable String username) {
        return userService.publicProfile(username, me.collegeId());
    }
}
