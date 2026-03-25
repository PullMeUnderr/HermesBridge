package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.tdlight", name = "enabled", havingValue = "true")
@ConditionalOnBean(TdlightPublicChannelClient.class)
public class DefaultTdlightPublicChannelGateway implements TdlightPublicChannelGateway {

    private final TdlightPublicChannelClient tdlightPublicChannelClient;

    public DefaultTdlightPublicChannelGateway(TdlightPublicChannelClient tdlightPublicChannelClient) {
        this.tdlightPublicChannelClient = tdlightPublicChannelClient;
    }

    @Override
    public TdlightPublicChannelPayload fetchPublicChannel(
        TdlightConnection connection,
        TdlightPublicChannelQuery query
    ) {
        TdlightPublicChannelClient.TdlightResolvedChannel channel = tdlightPublicChannelClient.resolvePublicChannel(
            connection,
            new TdlightPublicChannelClient.TdlightChannelReference(
                query.sourceChannelId(),
                query.sourceChannelHandle()
            )
        );

        List<TdlightPublicChannelClient.TdlightFetchedPost> fetchedPosts = tdlightPublicChannelClient.fetchNewPosts(
            connection,
            channel,
            new TdlightPublicChannelClient.TdlightFetchCursor(
                query.activatedAt(),
                query.lastSeenRemoteMessageId(),
                query.backfillHistoryEnabled(),
                query.initialHistoricalPostCount(),
                query.includeMedia()
            ),
            query.messageLimit() + Math.max(0, query.initialHistoricalPostCount())
        );

        return new TdlightPublicChannelPayload(
            channel.sourceChannelId(),
            channel.sourceChannelHandle(),
            channel.title(),
            fetchedPosts.stream()
                .map(post -> toPublicPostPayload(connection, channel, post, query.includeMedia()))
                .toList()
        );
    }

    private TdlightPublicPostPayload toPublicPostPayload(
        TdlightConnection connection,
        TdlightPublicChannelClient.TdlightResolvedChannel channel,
        TdlightPublicChannelClient.TdlightFetchedPost post,
        boolean includeMedia
    ) {
        List<TdlightPublicMediaPayload> media = includeMedia
            ? resolvePostMedia(connection, channel, post)
            : List.of();

        return new TdlightPublicPostPayload(
            post.remoteMessageId(),
            post.authorExternalId(),
            post.authorDisplayName(),
            post.text(),
            post.publishedAt(),
            media
        );
    }

    private List<TdlightPublicMediaPayload> resolvePostMedia(
        TdlightConnection connection,
        TdlightPublicChannelClient.TdlightResolvedChannel channel,
        TdlightPublicChannelClient.TdlightFetchedPost post
    ) {
        if (post.media() != null && !post.media().isEmpty()) {
            return post.media().stream()
                .filter(item -> item != null && item.content() != null && item.content().length > 0)
                .map(item -> new TdlightPublicMediaPayload(
                    item.fileName(),
                    item.mimeType(),
                    item.sizeBytes(),
                    item.durationSeconds(),
                    item.content()
                ))
                .toList();
        }

        if (post.mediaReferences() == null || post.mediaReferences().isEmpty()) {
            return List.of();
        }

        return post.mediaReferences().stream()
            .map(reference -> tdlightPublicChannelClient.fetchMedia(connection, channel, post, reference))
            .filter(item -> item != null && item.content() != null && item.content().length > 0)
            .map(item -> new TdlightPublicMediaPayload(
                item.fileName(),
                item.mimeType(),
                item.sizeBytes(),
                item.durationSeconds(),
                item.content()
            ))
            .toList();
    }
}
