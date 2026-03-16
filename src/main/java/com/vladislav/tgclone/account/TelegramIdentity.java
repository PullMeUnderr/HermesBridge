package com.vladislav.tgclone.account;

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
@Table(name = "telegram_identities")
public class TelegramIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @Column(name = "telegram_user_id", nullable = false, length = 255)
    private String telegramUserId;

    @Column(name = "telegram_username", length = 255)
    private String telegramUsername;

    @Column(name = "private_chat_id", nullable = false, length = 255)
    private String privateChatId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected TelegramIdentity() {
    }

    public TelegramIdentity(
        UserAccount userAccount,
        String telegramUserId,
        String telegramUsername,
        String privateChatId,
        Instant createdAt,
        Instant lastSeenAt
    ) {
        this.userAccount = userAccount;
        this.telegramUserId = telegramUserId;
        this.telegramUsername = telegramUsername;
        this.privateChatId = privateChatId;
        this.createdAt = createdAt;
        this.lastSeenAt = lastSeenAt;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public String getTelegramUserId() {
        return telegramUserId;
    }

    public String getTelegramUsername() {
        return telegramUsername;
    }

    public String getPrivateChatId() {
        return privateChatId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void touch(String telegramUsername, String privateChatId, Instant timestamp) {
        this.telegramUsername = telegramUsername;
        this.privateChatId = privateChatId;
        this.lastSeenAt = timestamp;
    }
}
