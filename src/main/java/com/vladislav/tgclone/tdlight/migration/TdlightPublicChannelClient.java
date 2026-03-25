package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import java.time.Instant;
import java.util.List;

public interface TdlightPublicChannelClient {

    TdlightResolvedChannel resolvePublicChannel(
        TdlightConnection connection,
        TdlightChannelReference reference
    );

    List<TdlightAvailableChannel> listAvailablePublicChannels(TdlightConnection connection);

    List<TdlightFetchedPost> fetchNewPosts(
        TdlightConnection connection,
        TdlightResolvedChannel channel,
        TdlightFetchCursor cursor,
        int limit
    );

    TdlightFetchedMedia fetchMedia(
        TdlightConnection connection,
        TdlightResolvedChannel channel,
        TdlightFetchedPost post,
        TdlightFetchedMediaReference mediaReference
    );

    TdlightFetchedMedia fetchChannelAvatar(
        TdlightConnection connection,
        TdlightResolvedChannel channel
    );

    record TdlightChannelReference(
        String sourceChannelId,
        String sourceChannelHandle
    ) {
    }

    record TdlightResolvedChannel(
        String sourceChannelId,
        String sourceChannelHandle,
        String title,
        String normalizedReference,
        TdlightChannelReferenceKind referenceKind,
        boolean publicChannel,
        TdlightChannelEligibility eligibility,
        String eligibilityReason
    ) {
    }

    enum TdlightChannelReferenceKind {
        HANDLE,
        NUMERIC_ID,
        UNKNOWN
    }

    enum TdlightChannelEligibility {
        ELIGIBLE,
        NOT_PUBLIC_CHANNEL,
        UNSUPPORTED_REFERENCE,
        UNKNOWN
    }

    record TdlightFetchCursor(
        Instant activatedAt,
        String lastSeenRemoteMessageId,
        boolean backfillHistoryEnabled,
        int initialHistoricalPostCount,
        boolean includeMedia
    ) {
    }

    record TdlightFetchedPost(
        String remoteMessageId,
        String authorExternalId,
        String authorDisplayName,
        String text,
        Instant publishedAt,
        List<TdlightFetchedMediaReference> mediaReferences,
        List<TdlightFetchedMedia> media
    ) {
    }

    record TdlightFetchedMediaReference(
        String remoteMediaId,
        String fileName,
        String mimeType,
        long sizeBytes,
        int durationSeconds
    ) {
    }

    record TdlightFetchedMedia(
        String fileName,
        String mimeType,
        long sizeBytes,
        int durationSeconds,
        byte[] content
    ) {
    }

    record TdlightAvailableChannel(
        String sourceChannelId,
        String sourceChannelHandle,
        String title,
        String avatarUrl
    ) {
    }
}
