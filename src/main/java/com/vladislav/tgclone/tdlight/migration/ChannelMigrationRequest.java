package com.vladislav.tgclone.tdlight.migration;

public record ChannelMigrationRequest(
    Long initiatedByUserId,
    Long tdlightConnectionId,
    String telegramChannelId,
    String telegramChannelHandle,
    boolean importMessages,
    boolean importMedia,
    boolean importAuthors
) {
}
