package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.conversation.ConversationAttachmentDraft;
import com.vladislav.tgclone.conversation.ConversationAttachmentKind;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TdlightMediaImportPlanner {

    public MediaImportPlan plan(
        TdlightChannelReader.TdlightChannelPost post,
        TdlightIngestionPolicy policy
    ) {
        if (!policy.mediaImportEnabled() || post.media() == null || post.media().isEmpty()) {
            return new MediaImportPlan(
                List.of(),
                0,
                mediaCount(post),
                post.media() == null
                    ? List.of()
                    : post.media().stream()
                        .filter(media -> media != null)
                        .map(media -> new MediaDecision(
                            media.fileName(),
                            media.mimeType(),
                            media.sizeBytes(),
                            media.durationSeconds(),
                            false,
                            "media import disabled",
                            null
                        ))
                        .toList()
            );
        }

        List<MediaDecision> decisions = post.media().stream()
            .filter(media -> media != null)
            .map(media -> classify(media, policy))
            .toList();

        List<ConversationAttachmentDraft> drafts = decisions.stream()
            .filter(MediaDecision::imported)
            .map(decision -> new ConversationAttachmentDraft(
                resolveAttachmentKind(decision.mimeType()),
                decision.fileName(),
                decision.mimeType(),
                decision.sizeBytes(),
                decision.content()
            ))
            .toList();

        return new MediaImportPlan(
            drafts,
            drafts.size(),
            Math.max(0, mediaCount(post) - drafts.size()),
            decisions
        );
    }

    private MediaDecision classify(TdlightChannelReader.TdlightChannelMedia media, TdlightIngestionPolicy policy) {
        if (media.content() == null || media.content().length == 0) {
            return new MediaDecision(
                media.fileName(),
                media.mimeType(),
                media.sizeBytes(),
                media.durationSeconds(),
                false,
                "missing content",
                null
            );
        }
        if (media.sizeBytes() > policy.maxImportedMediaBytes()) {
            return new MediaDecision(
                media.fileName(),
                media.mimeType(),
                media.sizeBytes(),
                media.durationSeconds(),
                false,
                "file exceeds maxImportedMediaBytes",
                null
            );
        }
        if (media.durationSeconds() > 0 && media.durationSeconds() > policy.maxImportedVideoDurationSeconds()) {
            return new MediaDecision(
                media.fileName(),
                media.mimeType(),
                media.sizeBytes(),
                media.durationSeconds(),
                false,
                "media exceeds maxImportedVideoDurationSeconds",
                null
            );
        }

        return new MediaDecision(
            media.fileName(),
            media.mimeType(),
            media.sizeBytes(),
            media.durationSeconds(),
            true,
            "imported",
            media.content()
        );
    }

    private int mediaCount(TdlightChannelReader.TdlightChannelPost post) {
        return post.media() == null ? 0 : post.media().size();
    }

    private ConversationAttachmentKind resolveAttachmentKind(String mimeType) {
        if (mimeType == null) {
            return ConversationAttachmentKind.DOCUMENT;
        }
        if (mimeType.startsWith("image/")) {
            return ConversationAttachmentKind.PHOTO;
        }
        if (mimeType.startsWith("video/")) {
            return ConversationAttachmentKind.VIDEO;
        }
        if (mimeType.startsWith("audio/")) {
            return ConversationAttachmentKind.VOICE;
        }
        return ConversationAttachmentKind.DOCUMENT;
    }

    public record MediaImportPlan(
        List<ConversationAttachmentDraft> drafts,
        int importedCount,
        int skippedCount,
        List<MediaDecision> decisions
    ) {
    }

    public record MediaDecision(
        String fileName,
        String mimeType,
        long sizeBytes,
        int durationSeconds,
        boolean imported,
        String reason,
        byte[] content
    ) {
    }
}
