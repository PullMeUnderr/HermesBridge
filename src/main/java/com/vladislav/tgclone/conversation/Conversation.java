package com.vladislav.tgclone.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_key", nullable = false, length = 100)
    private String tenantKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Conversation() {
    }

    public Conversation(String tenantKey, String title, Instant createdAt) {
        this.tenantKey = tenantKey;
        this.title = title;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
