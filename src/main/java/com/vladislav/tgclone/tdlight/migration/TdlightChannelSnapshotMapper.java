package com.vladislav.tgclone.tdlight.migration;

import java.util.List;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

@Component
public class TdlightChannelSnapshotMapper {

    public TdlightChannelReader.TdlightChannelSnapshot map(
        TdlightPublicChannelGateway.TdlightPublicChannelPayload payload,
        ChannelMigration migration,
        TdlightIngestionPolicy policy
    ) {
        if (payload == null) {
            throw new IllegalArgumentException("TDLight payload is required");
        }

        List<TdlightChannelReader.TdlightChannelPost> posts = payload.posts() == null
            ? List.of()
            : mapPosts(payload.posts(), migration, policy);

        return new TdlightChannelReader.TdlightChannelSnapshot(
            normalizeChannelId(payload.sourceChannelId(), migration.getSourceChannelId()),
            normalizeNullable(payload.sourceChannelHandle(), migration.getSourceChannelHandle()),
            normalizeChannelTitle(payload.channelTitle(), migration),
            posts
        );
    }

    private List<TdlightChannelReader.TdlightChannelPost> mapPosts(
        List<TdlightPublicChannelGateway.TdlightPublicPostPayload> rawPosts,
        ChannelMigration migration,
        TdlightIngestionPolicy policy
    ) {
        List<TdlightChannelReader.TdlightChannelPost> mappedPosts = new ArrayList<>();
        int remainingHistoricalContext = Math.max(0, policy.initialHistoricalPostCount());
        int totalLimit = Math.max(1, policy.publicChannelMessageImportLimit() + remainingHistoricalContext);

        for (TdlightPublicChannelGateway.TdlightPublicPostPayload post : rawPosts) {
            if (post == null || post.remoteMessageId() == null || post.remoteMessageId().isBlank()) {
                continue;
            }
            if (policy.lastSeenRemoteMessageId() != null && policy.lastSeenRemoteMessageId().equals(post.remoteMessageId())) {
                continue;
            }

            boolean historicalContextPost = !policy.backfillHistoryEnabled()
                && policy.lastSeenRemoteMessageId() == null
                && post.publishedAt() != null
                && post.publishedAt().isBefore(migration.getActivatedAt());
            if (historicalContextPost) {
                if (remainingHistoricalContext <= 0) {
                    continue;
                }
                remainingHistoricalContext -= 1;
            } else if (!policy.backfillHistoryEnabled()
                && post.publishedAt() != null
                && post.publishedAt().isBefore(migration.getActivatedAt())) {
                continue;
            }

            mappedPosts.add(mapPost(post));
            if (mappedPosts.size() >= totalLimit) {
                break;
            }
        }
        return mappedPosts;
    }

    private TdlightChannelReader.TdlightChannelPost mapPost(TdlightPublicChannelGateway.TdlightPublicPostPayload post) {
        List<TdlightChannelReader.TdlightChannelMedia> media = post.media() == null
            ? List.of()
            : post.media().stream()
                .filter(item -> item != null)
                .map(item -> new TdlightChannelReader.TdlightChannelMedia(
                    item.fileName(),
                    item.mimeType(),
                    item.sizeBytes(),
                    item.durationSeconds(),
                    item.content()
                ))
                .toList();

        return new TdlightChannelReader.TdlightChannelPost(
            post.remoteMessageId().trim(),
            normalizeNullable(post.authorExternalId(), null),
            normalizeAuthor(post.authorDisplayName()),
            normalizeBody(post.text()),
            post.publishedAt(),
            media
        );
    }

    private String normalizeChannelId(String value, String fallback) {
        String candidate = normalizeNullable(value, fallback);
        if (candidate == null) {
            throw new IllegalArgumentException("TDLight channel id is required");
        }
        return candidate;
    }

    private String normalizeChannelTitle(String value, ChannelMigration migration) {
        String candidate = normalizeNullable(value, null);
        if (candidate != null) {
            return candidate;
        }
        if (migration.getSourceChannelHandle() != null && !migration.getSourceChannelHandle().isBlank()) {
            return "Imported @" + migration.getSourceChannelHandle();
        }
        return "Imported " + migration.getSourceChannelId();
    }

    private String normalizeAuthor(String value) {
        String normalized = normalizeNullable(value, null);
        return normalized == null ? "Telegram channel" : normalized;
    }

    private String normalizeBody(String value) {
        return normalizeNullable(value, null);
    }

    private String normalizeNullable(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
