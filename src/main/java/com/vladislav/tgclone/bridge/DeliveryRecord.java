package com.vladislav.tgclone.bridge;

import com.vladislav.tgclone.conversation.ConversationMessage;
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
@Table(name = "delivery_records")
public class DeliveryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "conversation_message_id", nullable = false)
    private ConversationMessage conversationMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_transport", nullable = false, length = 30)
    private BridgeTransport targetTransport;

    @Column(name = "target_chat_id", nullable = false, length = 255)
    private String targetChatId;

    @Column(name = "target_message_id", nullable = false, length = 255)
    private String targetMessageId;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;

    protected DeliveryRecord() {
    }

    public DeliveryRecord(
        ConversationMessage conversationMessage,
        BridgeTransport targetTransport,
        String targetChatId,
        String targetMessageId,
        Instant deliveredAt
    ) {
        this.conversationMessage = conversationMessage;
        this.targetTransport = targetTransport;
        this.targetChatId = targetChatId;
        this.targetMessageId = targetMessageId;
        this.deliveredAt = deliveredAt;
    }

    public Long getId() {
        return id;
    }

    public ConversationMessage getConversationMessage() {
        return conversationMessage;
    }

    public BridgeTransport getTargetTransport() {
        return targetTransport;
    }

    public String getTargetChatId() {
        return targetChatId;
    }

    public String getTargetMessageId() {
        return targetMessageId;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
