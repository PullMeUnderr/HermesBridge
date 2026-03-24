package com.vladislav.tgclone.tdlight.connection;

import com.vladislav.tgclone.account.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tdlight_connections")
public class TdlightConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TdlightConnectionStatus status;

    @Column(name = "phone_mask", length = 50)
    private String phoneMask;

    @Column(name = "tdlight_user_id", length = 255)
    private String tdlightUserId;

    @Column(name = "tdlight_username", length = 255)
    private String tdlightUsername;

    @Column(name = "tdlight_display_name", length = 255)
    private String tdlightDisplayName;

    @Column(name = "encrypted_session_blob", columnDefinition = "text")
    private String encryptedSessionBlob;

    @Column(name = "encrypted_session_key_version", length = 100)
    private String encryptedSessionKeyVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    protected TdlightConnection() {
    }

    public TdlightConnection(
        UserAccount userAccount,
        TdlightConnectionStatus status,
        String phoneMask,
        String tdlightUserId,
        String tdlightUsername,
        String tdlightDisplayName,
        String encryptedSessionBlob,
        String encryptedSessionKeyVersion,
        Instant createdAt,
        Instant verifiedAt,
        Instant revokedAt,
        Instant lastSyncedAt
    ) {
        this.userAccount = userAccount;
        this.status = status;
        this.phoneMask = phoneMask;
        this.tdlightUserId = tdlightUserId;
        this.tdlightUsername = tdlightUsername;
        this.tdlightDisplayName = tdlightDisplayName;
        this.encryptedSessionBlob = encryptedSessionBlob;
        this.encryptedSessionKeyVersion = encryptedSessionKeyVersion;
        this.createdAt = createdAt;
        this.verifiedAt = verifiedAt;
        this.revokedAt = revokedAt;
        this.lastSyncedAt = lastSyncedAt;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public TdlightConnectionStatus getStatus() {
        return status;
    }

    public String getPhoneMask() {
        return phoneMask;
    }

    public String getTdlightUserId() {
        return tdlightUserId;
    }

    public String getTdlightUsername() {
        return tdlightUsername;
    }

    public String getTdlightDisplayName() {
        return tdlightDisplayName;
    }

    public String getEncryptedSessionBlob() {
        return encryptedSessionBlob;
    }

    public String getEncryptedSessionKeyVersion() {
        return encryptedSessionKeyVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public boolean isActive() {
        return status == TdlightConnectionStatus.ACTIVE;
    }

    public void markVerified(Instant timestamp) {
        this.status = TdlightConnectionStatus.ACTIVE;
        this.verifiedAt = timestamp;
        this.revokedAt = null;
    }

    public void updateAuthorizedProfile(
        String tdlightUserId,
        String tdlightUsername,
        String tdlightDisplayName,
        Instant timestamp
    ) {
        this.tdlightUserId = tdlightUserId;
        this.tdlightUsername = tdlightUsername;
        this.tdlightDisplayName = tdlightDisplayName;
        markVerified(timestamp);
    }

    public void markFailed() {
        this.status = TdlightConnectionStatus.FAILED;
    }

    public void markPaused() {
        this.status = TdlightConnectionStatus.PAUSED;
    }

    public void markRevoked(Instant timestamp) {
        this.status = TdlightConnectionStatus.REVOKED;
        this.revokedAt = timestamp;
    }

    public void markSynced(Instant timestamp) {
        this.lastSyncedAt = timestamp;
    }
}
