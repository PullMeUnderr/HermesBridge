package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.bridge.BridgeTransport;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "conversation_messages")
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_transport", nullable = false, length = 30)
    private BridgeTransport sourceTransport;

    @Column(name = "source_chat_id", length = 255)
    private String sourceChatId;

    @Column(name = "source_message_id", length = 255)
    private String sourceMessageId;

    @Column(name = "author_external_id", length = 255)
    private String authorExternalId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "author_user_id")
    private UserAccount authorUser;

    @Column(name = "author_display_name", nullable = false, length = 255)
    private String authorDisplayName;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private ConversationMessage replyToMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "message", fetch = FetchType.EAGER)
    private List<ConversationAttachment> attachments = new ArrayList<>();

    protected ConversationMessage() {
    }

    public ConversationMessage(
        Conversation conversation,
        BridgeTransport sourceTransport,
        String sourceChatId,
        String sourceMessageId,
        UserAccount authorUser,
        String authorExternalId,
        String authorDisplayName,
        String body,
        Instant createdAt
    ) {
        this(
            conversation,
            sourceTransport,
            sourceChatId,
            sourceMessageId,
            authorUser,
            authorExternalId,
            authorDisplayName,
            body,
            null,
            createdAt
        );
    }

    public ConversationMessage(
        Conversation conversation,
        BridgeTransport sourceTransport,
        String sourceChatId,
        String sourceMessageId,
        UserAccount authorUser,
        String authorExternalId,
        String authorDisplayName,
        String body,
        ConversationMessage replyToMessage,
        Instant createdAt
    ) {
        this.conversation = conversation;
        this.sourceTransport = sourceTransport;
        this.sourceChatId = sourceChatId;
        this.sourceMessageId = sourceMessageId;
        this.authorUser = authorUser;
        this.authorExternalId = authorExternalId;
        this.authorDisplayName = authorDisplayName;
        this.body = body;
        this.replyToMessage = replyToMessage;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public BridgeTransport getSourceTransport() {
        return sourceTransport;
    }

    public String getSourceChatId() {
        return sourceChatId;
    }

    public String getSourceMessageId() {
        return sourceMessageId;
    }

    public String getAuthorExternalId() {
        return authorExternalId;
    }

    public UserAccount getAuthorUser() {
        return authorUser;
    }

    public String getAuthorDisplayName() {
        return authorDisplayName;
    }

    public String getBody() {
        return body;
    }

    public ConversationMessage getReplyToMessage() {
        return replyToMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ConversationAttachment> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    void addAttachment(ConversationAttachment attachment) {
        this.attachments.add(attachment);
    }
}
