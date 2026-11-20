package com.loleague.auth.user;

import com.loleague.auth.college.CollegeCatalog;
import com.loleague.auth.config.AuthProperties;
import com.loleague.auth.sync.AuthorSyncClient;
import com.loleague.auth.user.UserDtos.*;
import com.loleague.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern USERNAME = Pattern.compile(UserDtos.USERNAME_REGEX);
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "root", "support", "help", "loleague", "moderator", "mod",
            "system", "null", "undefined", "me", "api", "official", "staff");

    private final UserRepository users;
    private final CollegeCatalog colleges;
    private final PasswordEncoder passwordEncoder;
    private final AuthorSyncClient authorSync;
    private final AuthProperties props;
    private final String mediaBaseUrl;
    /** Unknown usernames are checked against this so they take as long as wrong passwords (no timing leaks). */
    private final String dummyHash;

    public UserService(UserRepository users, CollegeCatalog colleges, PasswordEncoder passwordEncoder,
                       AuthorSyncClient authorSync, AuthProperties props,
                       @Value("${app.media-base-url:}") String mediaBaseUrl) {
        this.users = users;
        this.colleges = colleges;
        this.passwordEncoder = passwordEncoder;
        this.authorSync = authorSync;
        this.props = props;
        this.mediaBaseUrl = mediaBaseUrl;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-" + UUID.randomUUID());
    }

    public UsernameAvailability checkUsername(String raw) {
        String username = normalize(raw);
        if (!USERNAME.matcher(username).matches()) {
            return new UsernameAvailability(username, false, UserDtos.USERNAME_MESSAGE);
        }
        if (RESERVED.contains(username)) {
            return new UsernameAvailability(username, false, "That username is reserved");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            return new UsernameAvailability(username, false, "Already claimed. Try another one!");
        }
        return new UsernameAvailability(username, true, null);
    }

    @Transactional
    public User register(RegisterRequest req) {
        if (!req.password().equals(req.confirmPassword())) {
            throw ApiException.badRequest("Passwords don't match");
        }
        colleges.find(req.collegeId()).orElseThrow(() -> ApiException.badRequest("Unknown college"));
        UsernameAvailability availability = checkUsername(req.username());
        if (!availability.available()) {
            throw ApiException.conflict(availability.reason());
        }
        User user = new User(availability.username(), passwordEncoder.encode(req.password()), req.collegeId());
        if (props.admins().contains(user.getUsername())) {
            user.setRole("ADMIN");
        }
        users.saveAndFlush(user);
        authorSync.push(user);
        return user;
    }

    @Transactional(readOnly = true)
    public User authenticate(LoginRequest req) {
        String username = normalize(req.username());
        User user = users.findByUsernameIgnoreCase(username).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean ok = passwordEncoder.matches(req.password(), hash);
        if (user == null || !ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Wrong username or password");
        }
        return user;
    }

    @Transactional
    public User syncRole(User user) {
        // Lets you promote someone to admin via ADMIN_USERNAMES without touching the database.
        String expected = props.admins().contains(user.getUsername()) ? "ADMIN" : "USER";
        if (!expected.equals(user.getRole())) {
            user.setRole(expected);
            return users.save(user);
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public PublicProfile publicProfile(String username, String viewerCollegeId) {
        User user = users.findByUsernameIgnoreCase(normalize(username))
                .orElseThrow(() -> ApiException.notFound("User"));
        if (!user.getCollegeId().equals(viewerCollegeId)) {
            // Colleges are separate worlds.
            throw ApiException.notFound("User");
        }
        return new PublicProfile(user.getId(), user.getUsername(), user.getCollegeId(), collegeName(user.getCollegeId()),
                user.getDisplayName(), user.getBio(), user.getAvatarUrl(),
                user.getBirthday() == null ? null : user.getBirthday().getDayOfMonth(),
                user.getBirthday() == null ? null : user.getBirthday().getMonthValue(),
                user.getCreatedAt());
    }

    @Transactional
    public User updateProfile(UUID id, UpdateProfileRequest req) {
        User user = get(id);
        String avatar = blankToNull(req.avatarUrl());
        if (avatar != null && !isAllowedAvatarUrl(avatar, id)) {
            throw ApiException.badRequest("Avatar must be uploaded through LOLeague");
        }
        String displayName = blankToNull(req.displayName());
        user.updateProfile(
                displayName == null ? user.getUsername() : displayName.trim(),
                blankToNull(req.bio()),
                avatar,
                req.birthday());
        users.save(user);
        authorSync.push(user);
        return user;
    }

    @Transactional
    public User changePassword(UUID id, ChangePasswordRequest req) {
        User user = get(id);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is wrong");
        }
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw ApiException.badRequest("New passwords don't match");
        }
        if (req.newPassword().equals(req.currentPassword())) {
            throw ApiException.badRequest("New password must be different");
        }
        user.changePassword(passwordEncoder.encode(req.newPassword()));
        return users.save(user);
    }

    public UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getCollegeId(), collegeName(u.getCollegeId()),
                u.getDisplayName(), u.getBio(), u.getAvatarUrl(), u.getBirthday(), u.getRole(), u.getCreatedAt());
    }

    private String collegeName(String id) {
        return colleges.find(id).map(CollegeCatalog.College::name).orElse(id);
    }

    /** Avatars must come from our bucket, from this student's own avatars/ folder. */
    private boolean isAllowedAvatarUrl(String url, UUID userId) {
        boolean ownFolder = url.contains("/avatars/" + userId + "/");
        if (mediaBaseUrl == null || mediaBaseUrl.isBlank()) {
            return ownFolder && url.startsWith("https://");
        }
        return ownFolder && url.startsWith(mediaBaseUrl);
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
