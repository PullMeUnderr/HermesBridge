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
@Table(name = "tdlight_channel_subscriptions")
public class TdlightChannelSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tdlight_connection_id", nullable = false)
    private TdlightConnection tdlightConnection;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "telegram_channel_id", nullable = false, length = 255)
    private String telegramChannelId;

    @Column(name = "telegram_channel_handle", length = 255)
    private String telegramChannelHandle;

    @Column(name = "channel_title", nullable = false, length = 255)
    private String channelTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TdlightChannelSubscriptionStatus status;

    @Column(name = "subscribed_at", nullable = false)
    private Instant subscribedAt;

    @Column(name = "last_synced_remote_message_id", length = 255)
    private String lastSyncedRemoteMessageId;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TdlightChannelSubscription() {
    }

    public TdlightChannelSubscription(
        UserAccount userAccount,
        TdlightConnection tdlightConnection,
        Long conversationId,
        String telegramChannelId,
        String telegramChannelHandle,
        String channelTitle,
        TdlightChannelSubscriptionStatus status,
        Instant subscribedAt,
        String lastSyncedRemoteMessageId,
        Instant lastSyncedAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.userAccount = userAccount;
        this.tdlightConnection = tdlightConnection;
        this.conversationId = conversationId;
        this.telegramChannelId = telegramChannelId;
        this.telegramChannelHandle = telegramChannelHandle;
        this.channelTitle = channelTitle;
        this.status = status;
        this.subscribedAt = subscribedAt;
        this.lastSyncedRemoteMessageId = lastSyncedRemoteMessageId;
        this.lastSyncedAt = lastSyncedAt;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public TdlightConnection getTdlightConnection() {
        return tdlightConnection;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public String getTelegramChannelId() {
        return telegramChannelId;
    }

    public String getTelegramChannelHandle() {
        return telegramChannelHandle;
    }

    public String getChannelTitle() {
        return channelTitle;
    }

    public TdlightChannelSubscriptionStatus getStatus() {
        return status;
    }

    public Instant getSubscribedAt() {
        return subscribedAt;
    }

    public String getLastSyncedRemoteMessageId() {
        return lastSyncedRemoteMessageId;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markActive(Instant timestamp) {
        this.status = TdlightChannelSubscriptionStatus.ACTIVE;
        this.lastError = null;
        this.updatedAt = timestamp;
    }

    public void markFailed(String error, Instant timestamp) {
        this.status = TdlightChannelSubscriptionStatus.FAILED;
        this.lastError = error;
        this.updatedAt = timestamp;
    }

    public void advanceCursor(String remoteMessageId, Instant timestamp) {
        this.lastSyncedRemoteMessageId = remoteMessageId;
        this.lastSyncedAt = timestamp;
        this.updatedAt = timestamp;
        this.lastError = null;
    }

    public void rebindConnection(TdlightConnection tdlightConnection, Instant timestamp) {
        this.tdlightConnection = tdlightConnection;
        this.updatedAt = timestamp;
        this.lastError = null;
    }
}
