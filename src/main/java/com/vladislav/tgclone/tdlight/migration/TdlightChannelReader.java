package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import java.util.List;

public interface TdlightChannelReader {

    TdlightChannelSnapshot readPublicChannel(
        TdlightConnection connection,
        ChannelMigration migration,
        TdlightIngestionPolicy policy
    );

    record TdlightChannelSnapshot(
        String sourceChannelId,
        String sourceChannelHandle,
        String channelTitle,
        List<TdlightChannelPost> posts
    ) {
    }

    record TdlightChannelPost(
        String remoteMessageId,
        String authorExternalId,
        String authorDisplayName,
        String body,
        java.time.Instant publishedAt,
        List<TdlightChannelMedia> media
    ) {
    }

    record TdlightChannelMedia(
        String fileName,
        String mimeType,
        long sizeBytes,
        int durationSeconds,
        byte[] content
    ) {
    }
}
