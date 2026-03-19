package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.common.NotFoundException;
import com.vladislav.tgclone.media.MediaStorageService;
import com.vladislav.tgclone.media.StoredMediaFile;
import com.vladislav.tgclone.security.AuthenticatedUser;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationAttachmentService {

    private final ConversationAttachmentRepository conversationAttachmentRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MediaStorageService mediaStorageService;
    private final Clock clock;

    public ConversationAttachmentService(
        ConversationAttachmentRepository conversationAttachmentRepository,
        ConversationMemberRepository conversationMemberRepository,
        MediaStorageService mediaStorageService,
        Clock clock
    ) {
        this.conversationAttachmentRepository = conversationAttachmentRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.mediaStorageService = mediaStorageService;
        this.clock = clock;
    }

    @Transactional
    public List<ConversationAttachment> storeDrafts(ConversationMessage message, List<ConversationAttachmentDraft> drafts) {
        List<ConversationAttachment> attachments = new ArrayList<>();
        if (drafts == null || drafts.isEmpty()) {
            return attachments;
        }

        for (ConversationAttachmentDraft draft : drafts) {
            if (draft == null) {
                continue;
            }

            StoredMediaFile storedMediaFile = mediaStorageService.store(
                draft.originalFilename(),
                draft.mimeType(),
                draft.content()
            );

            ConversationAttachment attachment = conversationAttachmentRepository.save(
                new ConversationAttachment(
                    message,
                    normalizeKind(draft.kind()),
                    storedMediaFile.originalFilename(),
                    storedMediaFile.mimeType(),
                    storedMediaFile.sizeBytes(),
                    storedMediaFile.storageKey(),
                    clock.instant()
                )
            );
            message.addAttachment(attachment);
            attachments.add(attachment);
        }

        return attachments;
    }

    @Transactional(readOnly = true)
    public ResolvedConversationAttachment resolveForUser(AuthenticatedUser authenticatedUser, Long attachmentId) {
        ConversationAttachment attachment = conversationAttachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new NotFoundException("Attachment %s not found".formatted(attachmentId)));

        Long conversationId = attachment.getMessage().getConversation().getId();
        boolean hasAccess = conversationMemberRepository
            .findByConversation_IdAndUserAccount_Id(conversationId, authenticatedUser.userId())
            .filter(member -> member.getConversation().getTenantKey().equals(authenticatedUser.tenantKey()))
            .isPresent();

        if (!hasAccess) {
            throw new NotFoundException("Attachment %s not found".formatted(attachmentId));
        }

        return new ResolvedConversationAttachment(
            attachment
        );
    }

    private ConversationAttachmentKind normalizeKind(ConversationAttachmentKind kind) {
        return kind == null ? ConversationAttachmentKind.DOCUMENT : kind;
    }

    public record ResolvedConversationAttachment(
        ConversationAttachment attachment
    ) {
    }

    public String buildContentUrl(ConversationAttachment attachment) {
        if (attachment == null || attachment.getStorageKey() == null || attachment.getStorageKey().isBlank()) {
            return null;
        }

        String version = attachment.getCreatedAt() == null
            ? String.valueOf(attachment.getId())
            : String.valueOf(attachment.getCreatedAt().toEpochMilli());
        return "/api/attachments/" + attachment.getId() + "/content?v=" + version;
    }
}
