package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import com.vladislav.tgclone.tdlight.condition.ConditionalOnTdlightStubMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("local")
@Service
@ConditionalOnTdlightStubMode
public class StubTdlightPublicChannelClient implements TdlightPublicChannelClient {

    private final Clock clock;

    public StubTdlightPublicChannelClient(Clock clock) {
        this.clock = clock;
    }

    @Override
    public TdlightResolvedChannel resolvePublicChannel(
        TdlightConnection connection,
        TdlightChannelReference reference
    ) {
        String channelId = normalize(reference.sourceChannelId(), "public-channel");
        String channelHandle = normalize(reference.sourceChannelHandle(), null);
        String title = channelHandle == null ? "Imported " + channelId : "Imported @" + channelHandle;
        return new TdlightResolvedChannel(
            channelId,
            channelHandle,
            title,
            channelHandle == null ? channelId : "@" + channelHandle,
            resolveReferenceKind(channelId, channelHandle),
            true,
            TdlightChannelEligibility.ELIGIBLE,
            "Resolved as public-channel-compatible stub reference"
        );
    }

    @Override
    public List<TdlightFetchedPost> fetchNewPosts(
        TdlightConnection connection,
        TdlightResolvedChannel channel,
        TdlightFetchCursor cursor,
        int limit
    ) {
        Instant now = clock.instant();
        int importLimit = Math.max(1, Math.min(2, limit));
        List<TdlightFetchedPost> posts = new ArrayList<>();

        for (int sequence = 1; sequence <= importLimit; sequence += 1) {
            posts.add(
                new TdlightFetchedPost(
                    buildRemoteMessageId(cursor, sequence),
                    channel.sourceChannelId(),
                    resolveAuthorName(channel),
                    buildImportedBody(channel, connection, cursor, sequence),
                    now.plusSeconds(sequence * 5L),
                    cursor.includeMedia()
                        ? List.of(
                            new TdlightFetchedMediaReference(
                                "stub-media-" + sequence,
                                "tdlight-post-" + sequence + ".txt",
                                "text/plain",
                                estimateStubMediaSize(channel, sequence),
                                0
                            )
                        )
                        : List.of()
                )
            );
        }

        return posts;
    }

    @Override
    public TdlightFetchedMedia fetchMedia(
        TdlightConnection connection,
        TdlightResolvedChannel channel,
        TdlightFetchedPost post,
        TdlightFetchedMediaReference mediaReference
    ) {
        byte[] content = ("Stub TDLight media for " + channel.sourceChannelId() + " / " + post.remoteMessageId())
            .getBytes(StandardCharsets.UTF_8);

        return new TdlightFetchedMedia(
            mediaReference.fileName(),
            mediaReference.mimeType(),
            content.length,
            mediaReference.durationSeconds(),
            content
        );
    }

    private int estimateStubMediaSize(TdlightResolvedChannel channel, int sequence) {
        return ("Stub TDLight media for " + channel.sourceChannelId() + " #" + sequence)
            .getBytes(StandardCharsets.UTF_8).length;
    }

    private String buildImportedBody(
        TdlightResolvedChannel channel,
        TdlightConnection connection,
        TdlightFetchCursor cursor,
        int sequence
    ) {
        return """
            Stub TDLight post #%s

            Source channel: %s
            TDLight connection: %s
            Imported only because it is newer than activatedAt: %s
            Historical backfill is disabled for this MVP.
            """.formatted(
            sequence,
            channel.sourceChannelId(),
            connection.getId(),
            cursor.activatedAt()
        ).trim();
    }

    private String buildRemoteMessageId(TdlightFetchCursor cursor, int sequence) {
        String base = cursor.lastSeenRemoteMessageId() == null ? "initial" : cursor.lastSeenRemoteMessageId();
        return base + "-stub-" + sequence;
    }

    private String resolveAuthorName(TdlightResolvedChannel channel) {
        return channel.sourceChannelHandle() == null ? channel.sourceChannelId() : "@" + channel.sourceChannelHandle();
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private TdlightChannelReferenceKind resolveReferenceKind(String channelId, String channelHandle) {
        if (channelHandle != null && !channelHandle.isBlank()) {
            return TdlightChannelReferenceKind.HANDLE;
        }
        if (channelId != null && channelId.chars().allMatch(Character::isDigit)) {
            return TdlightChannelReferenceKind.NUMERIC_ID;
        }
        return TdlightChannelReferenceKind.UNKNOWN;
    }
}
