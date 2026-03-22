package com.vladislav.tgclone.conversation;

import java.time.Instant;

public record ConversationSocketSummaryPayload(
    Long id,
    String tenantKey,
    String title,
    String avatarUrl,
    String membershipRole,
    Instant createdAt,
    String lastMessagePreview,
    Instant lastMessageCreatedAt,
    long unreadCount,
    boolean hasUnreadMention,
    boolean muted
) {

    public static ConversationSocketSummaryPayload from(
        ConversationService.ConversationSummary summary,
        ConversationService conversationService
    ) {
        ConversationMember membership = summary.membership();
        Conversation conversation = membership.getConversation();
        return new ConversationSocketSummaryPayload(
            conversation.getId(),
            conversation.getTenantKey(),
            conversation.getTitle(),
            conversationService.buildConversationAvatarUrl(conversation),
            membership.getRole().name(),
            conversation.getCreatedAt(),
            summary.lastMessagePreview(),
            summary.lastMessageCreatedAt(),
            summary.unreadCount(),
            summary.hasUnreadMention(),
            membership.isMuted()
        );
    }
}
