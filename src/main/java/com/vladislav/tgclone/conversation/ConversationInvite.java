package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.account.UserAccount;
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
@Table(name = "conversation_invites")
public class ConversationInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private UserAccount createdByUser;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "accepted_by_user_id")
    private UserAccount acceptedByUser;

    @Column(name = "invite_hash", nullable = false, length = 128)
    private String inviteHash;

    @Column(name = "invite_prefix", nullable = false, length = 20)
    private String invitePrefix;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected ConversationInvite() {
    }

    public ConversationInvite(
        Conversation conversation,
        UserAccount createdByUser,
        String inviteHash,
        String invitePrefix,
        boolean revoked,
        Instant createdAt,
        Instant expiresAt
    ) {
        this.conversation = conversation;
        this.createdByUser = createdByUser;
        this.inviteHash = inviteHash;
        this.invitePrefix = invitePrefix;
        this.revoked = revoked;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public UserAccount getCreatedByUser() {
        return createdByUser;
    }

    public UserAccount getAcceptedByUser() {
        return acceptedByUser;
    }

    public String getInviteHash() {
        return inviteHash;
    }

    public String getInvitePrefix() {
        return invitePrefix;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public boolean isAvailableAt(Instant timestamp) {
        return !revoked
            && acceptedAt == null
            && (expiresAt == null || expiresAt.isAfter(timestamp));
    }

    public void markAccepted(UserAccount userAccount, Instant timestamp) {
        this.acceptedByUser = userAccount;
        this.acceptedAt = timestamp;
    }
}
