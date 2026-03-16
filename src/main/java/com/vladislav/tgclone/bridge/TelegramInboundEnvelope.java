package com.vladislav.tgclone.bridge;

import java.time.Instant;

public record TelegramInboundEnvelope(
    String externalChatId,
    String externalMessageId,
    String authorId,
    String authorDisplayName,
    String body,
    Instant createdAt
) {
}
