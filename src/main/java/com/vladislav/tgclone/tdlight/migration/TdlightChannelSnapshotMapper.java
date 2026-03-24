package com.vladislav.tgclone.tdlight.migration;

import java.util.List;
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
            : payload.posts().stream()
                .filter(post -> post != null && post.remoteMessageId() != null && !post.remoteMessageId().isBlank())
                .filter(post -> shouldIncludePost(post, migration, policy))
                .limit(Math.max(1, policy.publicChannelMessageImportLimit()))
                .map(this::mapPost)
                .toList();

        return new TdlightChannelReader.TdlightChannelSnapshot(
            normalizeChannelId(payload.sourceChannelId(), migration.getSourceChannelId()),
            normalizeNullable(payload.sourceChannelHandle(), migration.getSourceChannelHandle()),
            normalizeChannelTitle(payload.channelTitle(), migration),
            posts
        );
    }

    private boolean shouldIncludePost(
        TdlightPublicChannelGateway.TdlightPublicPostPayload post,
        ChannelMigration migration,
        TdlightIngestionPolicy policy
    ) {
        if (!policy.backfillHistoryEnabled()
            && post.publishedAt() != null
            && post.publishedAt().isBefore(migration.getActivatedAt())) {
            return false;
        }

        return policy.lastSeenRemoteMessageId() == null
            || !policy.lastSeenRemoteMessageId().equals(post.remoteMessageId());
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
        String normalized = normalizeNullable(value, null);
        return normalized == null ? "Imported post without text" : normalized;
    }

    private String normalizeNullable(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
