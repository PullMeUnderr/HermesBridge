package com.vladislav.tgclone.conversation;

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
@Table(name = "conversation_members")
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "invited_by_user_id")
    private UserAccount invitedByUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConversationMemberRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected ConversationMember() {
    }

    public ConversationMember(
        Conversation conversation,
        UserAccount userAccount,
        UserAccount invitedByUser,
        ConversationMemberRole role,
        Instant joinedAt
    ) {
        this.conversation = conversation;
        this.userAccount = userAccount;
        this.invitedByUser = invitedByUser;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public UserAccount getInvitedByUser() {
        return invitedByUser;
    }

    public ConversationMemberRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void updateRole(ConversationMemberRole role) {
        this.role = role;
    }
}
