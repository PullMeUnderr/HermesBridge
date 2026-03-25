package com.vladislav.tgclone.tdlight.migration;

public record TdlightChannelSubscriptionRequest(
    Long tdlightConnectionId,
    String telegramChannelId,
    String telegramChannelHandle,
    String channelTitle,
    String avatarUrl
) {
}
