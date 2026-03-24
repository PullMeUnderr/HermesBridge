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
@Table(name = "account_identities")
public class AccountIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_user_key", nullable = false, length = 255)
    private String providerUserKey;

    @Column(name = "secret_hash", length = 255)
    private String secretHash;

    @Column(name = "external_username", length = 255)
    private String externalUsername;

    @Column(name = "external_chat_id", length = 255)
    private String externalChatId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected AccountIdentity() {
    }

    public AccountIdentity(
        UserAccount userAccount,
        String provider,
        String providerUserKey,
        String secretHash,
        String externalUsername,
        String externalChatId,
        Instant verifiedAt,
        Instant createdAt,
        Instant lastSeenAt
    ) {
        this.userAccount = userAccount;
        this.provider = provider;
        this.providerUserKey = providerUserKey;
        this.secretHash = secretHash;
        this.externalUsername = externalUsername;
        this.externalChatId = externalChatId;
        this.verifiedAt = verifiedAt;
        this.createdAt = createdAt;
        this.lastSeenAt = lastSeenAt;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUserKey() {
        return providerUserKey;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public String getExternalUsername() {
        return externalUsername;
    }

    public String getExternalChatId() {
        return externalChatId;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void updateSecretHash(String secretHash, Instant verifiedAt) {
        this.secretHash = secretHash;
        this.verifiedAt = verifiedAt;
    }

    public void touchExternalIdentity(String externalUsername, String externalChatId, Instant timestamp) {
        this.externalUsername = externalUsername;
        this.externalChatId = externalChatId;
        this.verifiedAt = timestamp;
        this.lastSeenAt = timestamp;
    }
}
