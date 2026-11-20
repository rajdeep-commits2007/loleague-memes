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

    /** Permanent. No setter on purpose. */
    @Column(nullable = false, updatable = false, length = 20)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

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

    public User(String username, String passwordHash, String collegeId) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.collegeId = collegeId;
        this.displayName = username;
        this.createdAt = now;
        this.updatedAt = now;
        this.passwordChangedAt = now;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.passwordChangedAt = Instant.now();
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
    public String getCollegeId() { return collegeId; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public String getAvatarUrl() { return avatarUrl; }
    public LocalDate getBirthday() { return birthday; }
    public String getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
