package com.vladislav.tgclone.conversation;

import java.time.Instant;

public record ConversationReadPayload(
    Long conversationId,
    Long userId,
    String displayName,
    Instant readAt
) {
}
