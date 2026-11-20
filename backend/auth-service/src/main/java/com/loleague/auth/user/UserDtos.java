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

    public record RegisterRequest(
            @NotBlank(message = "Pick your college") String collegeId,
            @NotBlank(message = "Username is required")
            @Pattern(regexp = USERNAME_REGEX, message = USERNAME_MESSAGE) String username,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password,
            @NotBlank(message = "Confirm your password") String confirmPassword) {
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required") String password) {
    }

    public record UpdateProfileRequest(
            @Size(min = 1, max = 40, message = "Display name must be 1-40 characters") String displayName,
            @Size(max = 160, message = "Bio can be at most 160 characters") String bio,
            @Size(max = 512) String avatarUrl,
            @Past(message = "Birthday must be in the past") LocalDate birthday) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "New password must be 8-72 characters") String newPassword,
            @NotBlank(message = "Confirm your new password") String confirmPassword) {
    }

    public record UserResponse(UUID id, String username, String collegeId, String collegeName,
                               String displayName, String bio, String avatarUrl, LocalDate birthday,
                               String role, Instant createdAt) {
    }

    public record PublicProfile(UUID id, String username, String collegeId, String collegeName,
                                String displayName, String bio, String avatarUrl,
                                Integer birthdayDay, Integer birthdayMonth, Instant createdAt) {
    }

    public record UsernameAvailability(String username, boolean available, String reason) {
    }
}
