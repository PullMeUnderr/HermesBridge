package com.vladislav.tgclone.tdlight.migration;

import java.time.Instant;

public record TdlightChannelSubscriptionSummary(
    Long id,
    Long tdlightConnectionId,
    Long conversationId,
    String telegramChannelId,
    String telegramChannelHandle,
    String channelTitle,
    TdlightChannelSubscriptionStatus status,
    Instant subscribedAt,
    String lastSyncedRemoteMessageId,
    Instant lastSyncedAt,
    String lastError,
    Instant createdAt,
    Instant updatedAt
) {
    public static TdlightChannelSubscriptionSummary from(TdlightChannelSubscription subscription) {
        return new TdlightChannelSubscriptionSummary(
            subscription.getId(),
            subscription.getTdlightConnection().getId(),
            subscription.getConversationId(),
            subscription.getTelegramChannelId(),
            subscription.getTelegramChannelHandle(),
            subscription.getChannelTitle(),
            subscription.getStatus(),
            subscription.getSubscribedAt(),
            subscription.getLastSyncedRemoteMessageId(),
            subscription.getLastSyncedAt(),
            subscription.getLastError(),
            subscription.getCreatedAt(),
            subscription.getUpdatedAt()
        );
    }
}
