package com.vladislav.tgclone.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_key", nullable = false, length = 100)
    private String tenantKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "avatar_storage_key", length = 500)
    private String avatarStorageKey;

    @Column(name = "avatar_mime_type", length = 255)
    private String avatarMimeType;

    @Column(name = "avatar_original_filename", length = 255)
    private String avatarOriginalFilename;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Conversation() {
    }

    public Conversation(String tenantKey, String title, Instant createdAt) {
        this.tenantKey = tenantKey;
        this.title = title;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public String getTitle() {
        return title;
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

    public void updateTitle(String title) {
        this.title = title;
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
