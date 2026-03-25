package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import java.time.Instant;
import java.util.List;

public interface TdlightPublicChannelGateway {

    TdlightPublicChannelPayload fetchPublicChannel(
        TdlightConnection connection,
        TdlightPublicChannelQuery query
    );

    record TdlightPublicChannelQuery(
        String sourceChannelId,
        String sourceChannelHandle,
        Instant activatedAt,
        String lastSeenRemoteMessageId,
        boolean backfillHistoryEnabled,
        int messageLimit,
        int initialHistoricalPostCount,
        boolean includeMedia
    ) {
    }

    record TdlightPublicChannelPayload(
        String sourceChannelId,
        String sourceChannelHandle,
        String channelTitle,
        List<TdlightPublicPostPayload> posts
    ) {
    }

    record TdlightPublicPostPayload(
        String remoteMessageId,
        String authorExternalId,
        String authorDisplayName,
        String text,
        Instant publishedAt,
        List<TdlightPublicMediaPayload> media
    ) {
    }

    record TdlightPublicMediaPayload(
        String fileName,
        String mimeType,
        long sizeBytes,
        int durationSeconds,
        byte[] content
    ) {
    }
}
