package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tdlight_session_secrets")
public class TdlightSessionSecretEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tdlight_connection_id", nullable = false)
    private TdlightConnection tdlightConnection;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "session_binding_id", nullable = false)
    private TdlightSessionBindingEntity sessionBinding;

    @Column(name = "session_key", nullable = false, length = 255)
    private String sessionKey;

    @Column(name = "encrypted_session_blob", nullable = false, columnDefinition = "text")
    private String encryptedSessionBlob;

    @Column(name = "encryption_key_version", nullable = false, length = 100)
    private String encryptionKeyVersion;

    @Column(name = "session_fingerprint", length = 255)
    private String sessionFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected TdlightSessionSecretEntity() {
    }

    public TdlightSessionSecretEntity(
        TdlightConnection tdlightConnection,
        TdlightSessionBindingEntity sessionBinding,
        String sessionKey,
        String encryptedSessionBlob,
        String encryptionKeyVersion,
        String sessionFingerprint,
        Instant createdAt,
        Instant updatedAt,
        Instant revokedAt
    ) {
        this.tdlightConnection = tdlightConnection;
        this.sessionBinding = sessionBinding;
        this.sessionKey = sessionKey;
        this.encryptedSessionBlob = encryptedSessionBlob;
        this.encryptionKeyVersion = encryptionKeyVersion;
        this.sessionFingerprint = sessionFingerprint;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.revokedAt = revokedAt;
    }

    public Long getId() {
        return id;
    }

    public TdlightConnection getTdlightConnection() {
        return tdlightConnection;
    }

    public TdlightSessionBindingEntity getSessionBinding() {
        return sessionBinding;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getEncryptedSessionBlob() {
        return encryptedSessionBlob;
    }

    public String getEncryptionKeyVersion() {
        return encryptionKeyVersion;
    }

    public String getSessionFingerprint() {
        return sessionFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void markRevoked(Instant timestamp) {
        this.revokedAt = timestamp;
        this.updatedAt = timestamp;
    }
}
