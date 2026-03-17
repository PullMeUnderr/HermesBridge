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
@Table(name = "api_tokens")
public class ApiToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "token_prefix", nullable = false, length = 20)
    private String tokenPrefix;

    @Column(name = "plain_text_token", length = 255)
    private String plainTextToken;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected ApiToken() {
    }

    public ApiToken(
        UserAccount userAccount,
        String tokenHash,
        String tokenPrefix,
        String plainTextToken,
        String label,
        boolean revoked,
        Instant expiresAt,
        Instant createdAt
    ) {
        this.userAccount = userAccount;
        this.tokenHash = tokenHash;
        this.tokenPrefix = tokenPrefix;
        this.plainTextToken = plainTextToken;
        this.label = label;
        this.revoked = revoked;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public String getPlainTextToken() {
        return plainTextToken;
    }

    public String getLabel() {
        return label;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void revoke() {
        this.revoked = true;
    }

    public void markUsed(Instant timestamp) {
        this.lastUsedAt = timestamp;
    }
}
