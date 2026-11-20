package com.loleague.auth.user;

import com.loleague.auth.clerk.ClerkClient.ClerkIdentity;
import com.loleague.auth.college.CollegeCatalog;
import com.loleague.auth.config.AuthProperties;
import com.loleague.auth.sync.AuthorSyncClient;
import com.loleague.auth.user.UserDtos.*;
import com.loleague.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
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
    private final Duration usernameCooldown;
    /** Unknown usernames are checked against this so they take as long as wrong passwords (no timing leaks). */
    private final String dummyHash;

    public UserService(UserRepository users, CollegeCatalog colleges, PasswordEncoder passwordEncoder,
                       AuthorSyncClient authorSync, AuthProperties props,
                       @Value("${app.media-base-url:}") String mediaBaseUrl,
                       @Value("${app.username-change-cooldown:P7D}") Duration usernameCooldown) {
        this.users = users;
        this.colleges = colleges;
        this.passwordEncoder = passwordEncoder;
        this.authorSync = authorSync;
        this.props = props;
        this.mediaBaseUrl = mediaBaseUrl;
        this.usernameCooldown = usernameCooldown;
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

    @Transactional(readOnly = true)
    public Optional<User> findByClerk(String clerkUserId) {
        return users.findByClerkUserId(clerkUserId);
    }

    /**
     * Throws if this verified email already belongs to an account other than the given Clerk user,
     * so a second Clerk login (e.g. GitHub after Google) can never create a second account for one email.
     */
    @Transactional(readOnly = true)
    public void assertEmailFree(String email, String clerkUserId) {
        users.findByEmailIgnoreCase(email)
                .filter(u -> !clerkUserId.equals(u.getClerkUserId()))
                .ifPresent(u -> {
                    throw ApiException.conflict("This email is already linked to another LOLeague account. "
                            + "Sign in the way you did the first time.");
                });
    }

    /** Creates a brand-new account for a Clerk user with a verified email. */
    @Transactional
    public User register(ClerkIdentity clerk, String collegeId, String rawUsername) {
        requireEmail(clerk);
        if (users.findByClerkUserId(clerk.clerkUserId()).isPresent()) {
            throw ApiException.conflict("You already have an account. Just sign in.");
        }
        assertEmailFree(clerk.email(), clerk.clerkUserId());
        colleges.find(collegeId).orElseThrow(() -> ApiException.badRequest("Unknown college"));
        UsernameAvailability availability = checkUsername(rawUsername);
        if (!availability.available()) {
            throw ApiException.conflict(availability.reason());
        }
        User user = new User(availability.username(), collegeId, clerk.clerkUserId(), clerk.email());
        if (props.admins().contains(user.getUsername())) {
            user.setRole("ADMIN");
        }
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two tabs racing, or the email / username got taken between the checks and the insert.
            throw ApiException.conflict("That username or email was just taken. Try again.");
        }
        authorSync.push(user);
        return user;
    }

    /** Links a pre-Clerk (username + password) account to this Clerk user, once. */
    @Transactional
    public User claimLegacy(ClerkIdentity clerk, String rawUsername, String password) {
        requireEmail(clerk);
        if (users.findByClerkUserId(clerk.clerkUserId()).isPresent()) {
            throw ApiException.conflict("You already have an account. Just sign in.");
        }
        assertEmailFree(clerk.email(), clerk.clerkUserId());
        User user = users.findByUsernameIgnoreCase(normalize(rawUsername)).orElse(null);
        boolean claimable = user != null && user.getPasswordHash() != null && user.getClerkUserId() == null;
        String hash = claimable ? user.getPasswordHash() : dummyHash;
        boolean ok = passwordEncoder.matches(password, hash);
        if (!claimable || !ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Wrong username or password, or that account was already claimed");
        }
        user.linkClerk(clerk.clerkUserId(), clerk.email());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("This email is already linked to another LOLeague account.");
        }
    }

    /** Usernames stay unique (case-insensitive) but can change, at most once per cooldown. */
    @Transactional
    public User changeUsername(UUID id, String rawUsername) {
        User user = get(id);
        String wanted = normalize(rawUsername);
        if (wanted.equals(user.getUsername())) {
            return user;
        }
        if (user.getUsernameChangedAt() != null && !usernameCooldown.isZero()) {
            Instant next = user.getUsernameChangedAt().plus(usernameCooldown);
            if (next.isAfter(Instant.now())) {
                long days = Duration.between(Instant.now(), next).toHours() / 24 + 1;
                throw ApiException.badRequest("You can change your username again in " + days
                        + (days == 1 ? " day" : " days"));
            }
        }
        UsernameAvailability availability = checkUsername(wanted);
        if (!availability.available()) {
            throw ApiException.conflict(availability.reason());
        }
        boolean followUsername = user.getUsername().equals(user.getDisplayName());
        user.rename(availability.username());
        if (followUsername) {
            // The display name was never customised, so it follows the new username.
            user.updateProfile(user.getUsername(), user.getBio(), user.getAvatarUrl(), user.getBirthday());
        }
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("Already claimed. Try another one!");
        }
        // meme-service has its own unique index on usernames, so this call is synchronous: if it fails, the
        // rename rolls back instead of leaving the two services disagreeing.
        authorSync.pushNow(user);
        authorSync.renameInChat(user);
        return syncRole(user);
    }

    private static void requireEmail(ClerkIdentity clerk) {
        if (clerk.email() == null) {
            throw ApiException.badRequest("Verify your email address first");
        }
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

    public UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getCollegeId(), collegeName(u.getCollegeId()),
                u.getDisplayName(), u.getBio(), u.getAvatarUrl(), u.getBirthday(), u.getRole(), u.getEmail(),
                u.getUsernameChangedAt(), u.getCreatedAt());
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
