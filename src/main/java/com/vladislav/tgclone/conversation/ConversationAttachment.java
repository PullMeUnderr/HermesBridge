package com.vladislav.tgclone.conversation;

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
@Table(name = "conversation_attachments")
public class ConversationAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_message_id", nullable = false)
    private ConversationMessage message;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_kind", nullable = false, length = 30)
    private ConversationAttachmentKind kind;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 255)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConversationAttachment() {
    }

    public ConversationAttachment(
        ConversationMessage message,
        ConversationAttachmentKind kind,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String storageKey,
        Instant createdAt
    ) {
        this.message = message;
        this.kind = kind;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public ConversationMessage getMessage() {
        return message;
    }

    public ConversationAttachmentKind getKind() {
        return kind;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
