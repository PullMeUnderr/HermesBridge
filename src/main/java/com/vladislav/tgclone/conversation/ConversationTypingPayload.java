package com.vladislav.tgclone.conversation;

public record ConversationTypingPayload(
    Long conversationId,
    Long userId,
    String displayName,
    boolean active
) {
}
