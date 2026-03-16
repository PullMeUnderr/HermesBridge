package com.vladislav.tgclone.bridge;

import com.vladislav.tgclone.conversation.Conversation;
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
@Table(name = "transport_bindings")
public class TransportBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BridgeTransport transport;

    @Column(name = "external_chat_id", nullable = false, length = 255)
    private String externalChatId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransportBinding() {
    }

    public TransportBinding(
        Conversation conversation,
        BridgeTransport transport,
        String externalChatId,
        boolean active,
        Instant createdAt
    ) {
        this.conversation = conversation;
        this.transport = transport;
        this.externalChatId = externalChatId;
        this.active = active;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public BridgeTransport getTransport() {
        return transport;
    }

    public String getExternalChatId() {
        return externalChatId;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
