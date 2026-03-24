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
@Table(name = "tdlight_session_bindings")
public class TdlightSessionBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tdlight_connection_id", nullable = false)
    private TdlightConnection tdlightConnection;

    @Column(name = "session_key", nullable = false, length = 255)
    private String sessionKey;

    @Column(name = "database_directory", nullable = false, length = 1000)
    private String databaseDirectory;

    @Column(name = "files_directory", nullable = false, length = 1000)
    private String filesDirectory;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_opened_at")
    private Instant lastOpenedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected TdlightSessionBindingEntity() {
    }

    public TdlightSessionBindingEntity(
        TdlightConnection tdlightConnection,
        String sessionKey,
        String databaseDirectory,
        String filesDirectory,
        Instant createdAt,
        Instant updatedAt,
        Instant lastOpenedAt,
        Instant revokedAt
    ) {
        this.tdlightConnection = tdlightConnection;
        this.sessionKey = sessionKey;
        this.databaseDirectory = databaseDirectory;
        this.filesDirectory = filesDirectory;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastOpenedAt = lastOpenedAt;
        this.revokedAt = revokedAt;
    }

    public Long getId() {
        return id;
    }

    public TdlightConnection getTdlightConnection() {
        return tdlightConnection;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getDatabaseDirectory() {
        return databaseDirectory;
    }

    public String getFilesDirectory() {
        return filesDirectory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastOpenedAt() {
        return lastOpenedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void markOpened(Instant timestamp) {
        this.lastOpenedAt = timestamp;
        this.updatedAt = timestamp;
    }

    public void markRevoked(Instant timestamp) {
        this.revokedAt = timestamp;
        this.updatedAt = timestamp;
    }
}
