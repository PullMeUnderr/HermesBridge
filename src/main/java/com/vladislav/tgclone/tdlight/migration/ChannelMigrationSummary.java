package com.vladislav.tgclone.tdlight.migration;

import java.time.Instant;

public record ChannelMigrationSummary(
    Long id,
    Long initiatedByUserId,
    Long tdlightConnectionId,
    Long targetConversationId,
    String telegramChannelId,
    String telegramChannelHandle,
    ChannelMigrationStatus status,
    int importedMessageCount,
    int importedMediaCount,
    String lastError,
    Instant createdAt,
    Instant updatedAt
) {
}
