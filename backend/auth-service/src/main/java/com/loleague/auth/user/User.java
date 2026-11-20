package com.loleague.auth.user;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    /** Unique (case-insensitive). Can be changed with {@link #rename(String)}. */
    @Column(nullable = false, length = 20)
    private String username;

    /** Only set on pre-Clerk accounts until they are claimed. */
    @Column(name = "password_hash")
    private String passwordHash;

    /** Clerk user id (user_...). Set once, never changed. */
    @Column(name = "clerk_user_id", length = 64)
    private String clerkUserId;

    /** Verified phone number in E.164 form. One account per phone. */
    @Column(length = 20)
    private String phone;

    @Column(name = "username_changed_at")
    private Instant usernameChangedAt;

    /** Permanent. No setter on purpose. */
    @Column(name = "college_id", nullable = false, updatable = false, length = 80)
    private String collegeId;

    @Column(name = "display_name", length = 40)
    private String displayName;

    @Column(length = 160)
    private String bio;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    private LocalDate birthday;

    @Column(nullable = false, length = 16)
    private String role = "USER";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    protected User() {
    }

    public User(String username, String collegeId, String clerkUserId, String phone) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.username = username;
        this.collegeId = collegeId;
        this.clerkUserId = clerkUserId;
        this.phone = phone;
        this.displayName = username;
        this.createdAt = now;
        this.updatedAt = now;
        this.passwordChangedAt = now;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /** Attaches a pre-Clerk account to a Clerk login and its verified phone; the old password stops working. */
    public void linkClerk(String clerkUserId, String phone) {
        this.clerkUserId = clerkUserId;
        this.phone = phone;
        this.passwordHash = null;
        this.passwordChangedAt = Instant.now();
    }

    public void rename(String newUsername) {
        this.username = newUsername;
        this.usernameChangedAt = Instant.now();
    }

    public void updateProfile(String displayName, String bio, String avatarUrl, LocalDate birthday) {
        this.displayName = displayName;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.birthday = birthday;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getClerkUserId() { return clerkUserId; }
    public String getPhone() { return phone; }
    public Instant getUsernameChangedAt() { return usernameChangedAt; }
    public String getCollegeId() { return collegeId; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public String getAvatarUrl() { return avatarUrl; }
    public LocalDate getBirthday() { return birthday; }
    public String getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
