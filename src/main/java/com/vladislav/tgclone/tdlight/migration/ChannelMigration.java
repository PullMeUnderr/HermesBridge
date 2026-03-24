package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
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
@Table(name = "channel_migrations")
public class ChannelMigration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "initiated_by_user_id", nullable = false)
    private UserAccount initiatedByUser;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tdlight_connection_id", nullable = false)
    private TdlightConnection tdlightConnection;

    @Column(name = "target_conversation_id")
    private Long targetConversationId;

    @Column(name = "source_channel_id", nullable = false, length = 255)
    private String sourceChannelId;

    @Column(name = "source_channel_handle", length = 255)
    private String sourceChannelHandle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ChannelMigrationStatus status;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    @Column(name = "last_seen_remote_message_id", length = 255)
    private String lastSeenRemoteMessageId;

    @Column(name = "imported_message_count", nullable = false)
    private int importedMessageCount;

    @Column(name = "imported_media_count", nullable = false)
    private int importedMediaCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "retention_until")
    private Instant retentionUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChannelMigration() {
    }

    public ChannelMigration(
        UserAccount initiatedByUser,
        TdlightConnection tdlightConnection,
        Long targetConversationId,
        String sourceChannelId,
        String sourceChannelHandle,
        ChannelMigrationStatus status,
        Instant activatedAt,
        String lastSeenRemoteMessageId,
        int importedMessageCount,
        int importedMediaCount,
        String lastError,
        Instant retentionUntil,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.initiatedByUser = initiatedByUser;
        this.tdlightConnection = tdlightConnection;
        this.targetConversationId = targetConversationId;
        this.sourceChannelId = sourceChannelId;
        this.sourceChannelHandle = sourceChannelHandle;
        this.status = status;
        this.activatedAt = activatedAt;
        this.lastSeenRemoteMessageId = lastSeenRemoteMessageId;
        this.importedMessageCount = importedMessageCount;
        this.importedMediaCount = importedMediaCount;
        this.lastError = lastError;
        this.retentionUntil = retentionUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getInitiatedByUser() {
        return initiatedByUser;
    }

    public TdlightConnection getTdlightConnection() {
        return tdlightConnection;
    }

    public Long getTargetConversationId() {
        return targetConversationId;
    }

    public String getSourceChannelId() {
        return sourceChannelId;
    }

    public String getSourceChannelHandle() {
        return sourceChannelHandle;
    }

    public ChannelMigrationStatus getStatus() {
        return status;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public String getLastSeenRemoteMessageId() {
        return lastSeenRemoteMessageId;
    }

    public int getImportedMessageCount() {
        return importedMessageCount;
    }

    public int getImportedMediaCount() {
        return importedMediaCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getRetentionUntil() {
        return retentionUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markRunning(Instant timestamp) {
        this.status = ChannelMigrationStatus.RUNNING;
        this.updatedAt = timestamp;
        this.lastError = null;
    }

    public void markFailed(String lastError, Instant timestamp) {
        this.status = ChannelMigrationStatus.FAILED;
        this.lastError = lastError;
        this.updatedAt = timestamp;
    }

    public void markCompleted(Instant timestamp) {
        this.status = ChannelMigrationStatus.COMPLETED;
        this.updatedAt = timestamp;
        this.lastError = null;
    }

    public void markQueued(Instant timestamp) {
        this.status = ChannelMigrationStatus.QUEUED;
        this.updatedAt = timestamp;
        this.lastError = null;
    }

    public void bindTargetConversation(Long targetConversationId, Instant timestamp) {
        this.targetConversationId = targetConversationId;
        this.updatedAt = timestamp;
    }

    public void advanceCursor(String remoteMessageId, int messageDelta, int mediaDelta, Instant timestamp) {
        this.lastSeenRemoteMessageId = remoteMessageId;
        this.importedMessageCount += Math.max(0, messageDelta);
        this.importedMediaCount += Math.max(0, mediaDelta);
        this.updatedAt = timestamp;
    }
}
