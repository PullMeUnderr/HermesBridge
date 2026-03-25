package com.vladislav.tgclone.tdlight.migration;

public record TdlightAvailableChannelSummary(
    String telegramChannelId,
    String telegramChannelHandle,
    String channelTitle,
    String avatarUrl,
    boolean subscribed,
    Long subscriptionId,
    Long conversationId
) {
}
