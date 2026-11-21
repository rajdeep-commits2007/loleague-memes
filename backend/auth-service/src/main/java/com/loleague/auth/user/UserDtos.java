package com.loleague.auth.user;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public static final String USERNAME_REGEX = "^[A-Za-z0-9][A-Za-z0-9._]{1,18}[A-Za-z0-9]$";
    public static final String USERNAME_MESSAGE =
            "Username must be 3-20 characters: letters, numbers, dot or underscore (not at the ends)";

    /** A fresh Clerk session token from the browser ({@code getToken()}); valid for about a minute. */
    public record ClerkSessionRequest(@NotBlank(message = "Missing Clerk token") String token) {
    }

    /** First sign-in: pick a college (permanent) and a unique username. */
    public record OnboardRequest(
            @NotBlank(message = "Missing Clerk token") String token,
            @NotBlank(message = "Pick your college") String collegeId,
            @NotBlank(message = "Username is required")
            @Pattern(regexp = USERNAME_REGEX, message = USERNAME_MESSAGE) String username) {
    }

    /** First sign-in of someone who had a pre-Clerk account: prove it once with the old password. */
    public record ClaimRequest(
            @NotBlank(message = "Missing Clerk token") String token,
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required") String password) {
    }

    public record ChangeUsernameRequest(
            @NotBlank(message = "Username is required")
            @Pattern(regexp = USERNAME_REGEX, message = USERNAME_MESSAGE) String username) {
    }

    /**
     * Result of exchanging a Clerk token.
     * SIGNED_IN: cookies were set. ONBOARDING: no account yet for this Clerk user + email.
     * EMAIL_REQUIRED: the Clerk user has no verified email address yet.
     */
    public record SessionResult(String status, UserResponse user, String email, String suggestedName) {
        public static SessionResult signedIn(UserResponse user) {
            return new SessionResult("SIGNED_IN", user, null, null);
        }
    }

    public record UpdateProfileRequest(
            @Size(min = 1, max = 40, message = "Display name must be 1-40 characters") String displayName,
            @Size(max = 160, message = "Bio can be at most 160 characters") String bio,
            @Size(max = 512) String avatarUrl,
            @Past(message = "Birthday must be in the past") LocalDate birthday) {
    }

    public record UserResponse(UUID id, String username, String collegeId, String collegeName,
                               String displayName, String bio, String avatarUrl, LocalDate birthday,
                               String role, String email, Instant usernameChangedAt, Instant createdAt) {
    }

    public record PublicProfile(UUID id, String username, String collegeId, String collegeName,
                                String displayName, String bio, String avatarUrl,
                                Integer birthdayDay, Integer birthdayMonth, Instant createdAt) {
    }

    public record UsernameAvailability(String username, boolean available, String reason) {
    }
}
