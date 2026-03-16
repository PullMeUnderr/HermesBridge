package com.vladislav.tgclone.bridge;

import com.vladislav.tgclone.conversation.ConversationAttachmentDraft;
import java.time.Instant;
import java.util.List;

public record TelegramInboundEnvelope(
    String externalChatId,
    String externalMessageId,
    String authorId,
    String authorDisplayName,
    String body,
    Instant createdAt,
    List<ConversationAttachmentDraft> attachments
) {
}
