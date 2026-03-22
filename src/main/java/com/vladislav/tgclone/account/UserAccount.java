package com.vladislav.tgclone.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_key", nullable = false, length = 100)
    private String tenantKey;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "avatar_storage_key", length = 500)
    private String avatarStorageKey;

    @Column(name = "avatar_mime_type", length = 255)
    private String avatarMimeType;

    @Column(name = "avatar_original_filename", length = 255)
    private String avatarOriginalFilename;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    public UserAccount(
        String tenantKey,
        String username,
        String displayName,
        boolean active,
        Instant createdAt
    ) {
        this.tenantKey = tenantKey;
        this.username = username;
        this.displayName = displayName;
        this.active = active;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return active;
    }

    public String getAvatarStorageKey() {
        return avatarStorageKey;
    }

    public String getAvatarMimeType() {
        return avatarMimeType;
    }

    public String getAvatarOriginalFilename() {
        return avatarOriginalFilename;
    }

    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updateUsername(String username) {
        this.username = username;
    }

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void activate() {
        this.active = true;
    }

    public void updateAvatar(
        String avatarStorageKey,
        String avatarMimeType,
        String avatarOriginalFilename,
        Instant avatarUpdatedAt
    ) {
        this.avatarStorageKey = avatarStorageKey;
        this.avatarMimeType = avatarMimeType;
        this.avatarOriginalFilename = avatarOriginalFilename;
        this.avatarUpdatedAt = avatarUpdatedAt;
    }

    public void clearAvatar() {
        this.avatarStorageKey = null;
        this.avatarMimeType = null;
        this.avatarOriginalFilename = null;
        this.avatarUpdatedAt = null;
    }
}
