package com.vladislav.tgclone.tdlight.migration;

import java.time.Instant;

public record TdlightIngestionPolicy(
    boolean backfillHistoryEnabled,
    int publicChannelMessageImportLimit,
    int initialHistoricalPostCount,
    int importedPostRetentionDays,
    boolean mediaImportEnabled,
    long maxImportedMediaBytes,
    int maxImportedVideoDurationSeconds,
    Instant activatedAt,
    String lastSeenRemoteMessageId
) {
}
