package com.vladislav.tgclone.conversation;

import java.time.Instant;

public record ConversationSocketEvent(
    String eventId,
    String type,
    Long conversationId,
    Instant occurredAt,
    Object payload
) {

    public static ConversationSocketEvent newMessage(
        ConversationMessage message,
        ConversationAttachmentService conversationAttachmentService
    ) {
        return new ConversationSocketEvent(
            buildEventId("new_message", message.getId()),
            "new_message",
            message.getConversation().getId(),
            message.getCreatedAt(),
            ConversationMessageResponse.from(message, conversationAttachmentService)
        );
    }

    public static ConversationSocketEvent conversationSummary(
        ConversationSocketSummaryPayload summary
    ) {
        return new ConversationSocketEvent(
            buildEventId("conversation_summary", summary.id()),
            "conversation_summary",
            summary.id(),
            summary.lastMessageCreatedAt() == null ? summary.createdAt() : summary.lastMessageCreatedAt(),
            summary
        );
    }

    public static ConversationSocketEvent typing(
        ConversationTypingPayload payload
    ) {
        return new ConversationSocketEvent(
            buildEventId(payload.active() ? "typing_start" : "typing_stop", payload.userId()),
            payload.active() ? "typing_start" : "typing_stop",
            payload.conversationId(),
            Instant.now(),
            payload
        );
    }

    public static ConversationSocketEvent conversationRead(
        ConversationReadPayload payload
    ) {
        return new ConversationSocketEvent(
            buildEventId("conversation_read", payload.userId()),
            "conversation_read",
            payload.conversationId(),
            payload.readAt(),
            payload
        );
    }

    private static String buildEventId(String type, Long entityId) {
        String normalizedType = type == null || type.isBlank() ? "event" : type.trim();
        String normalizedEntityId = entityId == null ? "unknown" : String.valueOf(entityId);
        return normalizedType + ":" + normalizedEntityId;
    }
}
