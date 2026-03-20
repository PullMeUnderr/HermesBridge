package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.account.TelegramIdentity;
import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.bridge.MessageRelayService;
import com.vladislav.tgclone.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.vladislav.tgclone.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageRelayService messageRelayService;
    private final ConversationAttachmentService conversationAttachmentService;
    private final UserAccountService userAccountService;

    public ConversationController(
        ConversationService conversationService,
        MessageRelayService messageRelayService,
        ConversationAttachmentService conversationAttachmentService,
        UserAccountService userAccountService
    ) {
        this.conversationService = conversationService;
        this.messageRelayService = messageRelayService;
        this.conversationAttachmentService = conversationAttachmentService;
        this.userAccountService = userAccountService;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody CreateConversationRequest request
    ) {
        ConversationMember membership = conversationService.createConversation(authenticatedUser, request.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(
            toConversationResponse(new ConversationService.ConversationSummary(membership, null, null, 0))
        );
    }

    @GetMapping
    public List<ConversationResponse> listConversations(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return conversationService.listConversationSummaries(authenticatedUser).stream()
            .map(this::toConversationResponse)
            .toList();
    }

    @GetMapping("/{conversationId}")
    public ConversationResponse getConversation(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId
    ) {
        return toConversationResponse(
            new ConversationService.ConversationSummary(
                conversationService.requireMembership(authenticatedUser, conversationId),
                null,
                null,
                0
            )
        );
    }

    @PatchMapping(path = "/{conversationId}/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ConversationResponse updateConversationProfile(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId,
        @RequestParam String title,
        @RequestParam(name = "avatar", required = false) MultipartFile avatar,
        @RequestParam(name = "removeAvatar", defaultValue = "false") boolean removeAvatar
    ) {
        ConversationService.AvatarUpload avatarUpload = toConversationAvatarUpload(avatar);
        ConversationMember membership = conversationService.updateConversationProfile(
            authenticatedUser,
            conversationId,
            title,
            avatarUpload,
            removeAvatar
        );
        return toConversationResponse(new ConversationService.ConversationSummary(membership, null, null, 0));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId
    ) {
        conversationService.deleteConversation(authenticatedUser, conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{conversationId}/avatar")
    public ResponseEntity<InputStreamResource> conversationAvatar(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId
    ) throws IOException {
        ConversationService.ResolvedConversationAvatar resolvedConversationAvatar =
            conversationService.resolveConversationAvatar(authenticatedUser, conversationId);
        Conversation conversation = resolvedConversationAvatar.conversation();
        InputStreamResource body;
        try {
            body = new InputStreamResource(conversationService.openAvatarStream(conversation));
        } catch (IllegalStateException ex) {
            throw new NotFoundException("Conversation avatar not found");
        }

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().mustRevalidate())
            .contentType(resolveMediaType(conversation.getAvatarMimeType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(conversation.getAvatarOriginalFilename()).build().toString()
            )
            .header(HttpHeaders.PRAGMA, "no-cache")
            .header(HttpHeaders.EXPIRES, "0")
            .body(body);
    }

    @GetMapping("/{conversationId}/messages")
    public List<ConversationMessageResponse> listMessages(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId
    ) {
        return conversationService.listMessages(authenticatedUser, conversationId).stream()
            .map(message -> ConversationMessageResponse.from(message, conversationAttachmentService))
            .toList();
    }

    @PostMapping("/{conversationId}/read")
    public ResponseEntity<Void> markConversationRead(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId
    ) {
        conversationService.markConversationRead(authenticatedUser, conversationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ConversationMessageResponse> postMessage(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId,
        @Valid @RequestBody CreateConversationMessageRequest request
    ) {
        ConversationMessage message = conversationService.createInternalMessage(
            authenticatedUser,
            conversationId,
            request.body(),
            request.replyToMessageId()
        );
        messageRelayService.relayInternalMessage(message);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ConversationMessageResponse.from(message, conversationAttachmentService)
        );
    }

    @PostMapping(
        path = "/{conversationId}/messages/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ConversationMessageResponse> uploadMessage(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId,
        @RequestParam(required = false) String body,
        @RequestParam(required = false) Long replyToMessageId,
        @RequestParam(name = "files", required = false) List<MultipartFile> files,
        @RequestParam(name = "sendAsVideoNote", defaultValue = "false") boolean sendAsVideoNote,
        @RequestParam(name = "sendAsVoice", defaultValue = "false") boolean sendAsVoice
    ) {
        List<ConversationAttachmentDraft> attachments = toAttachmentDrafts(files, sendAsVideoNote, sendAsVoice);
        ConversationMessage message = conversationService.createInternalMessage(
            authenticatedUser,
            conversationId,
            body,
            replyToMessageId,
            attachments
        );
        messageRelayService.relayInternalMessage(message);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ConversationMessageResponse.from(message, conversationAttachmentService)
        );
    }

    @GetMapping("/{conversationId}/members")
    public List<ConversationMemberResponse> listMembers(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId
    ) {
        List<ConversationMember> members = conversationService.listMembers(authenticatedUser, conversationId);
        Map<Long, TelegramIdentity> identitiesByUserId = conversationService.findTelegramIdentitiesByUserIds(
            members.stream().map(member -> member.getUserAccount().getId()).toList()
        );

        return members.stream()
            .map(member -> {
                TelegramIdentity telegramIdentity = identitiesByUserId.get(member.getUserAccount().getId());
                return ConversationMemberResponse.from(
                    member,
                    telegramIdentity,
                    conversationService.isOnline(telegramIdentity),
                    userAccountService
                );
            })
            .toList();
    }

    @PatchMapping("/{conversationId}/preferences")
    public ConversationResponse updateConversationPreferences(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId,
        @Valid @RequestBody UpdateConversationPreferencesRequest request
    ) {
        ConversationMember membership = conversationService.updateConversationPreferences(
            authenticatedUser,
            conversationId,
            request.muted()
        );
        return toConversationResponse(new ConversationService.ConversationSummary(membership, null, null, 0));
    }

    @PostMapping("/{conversationId}/invites")
    public ResponseEntity<ConversationInviteResponse> createInvite(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId
    ) {
        IssuedConversationInvite issuedInvite = conversationService.createInvite(authenticatedUser, conversationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversationInviteResponse.from(issuedInvite));
    }

    @PatchMapping("/{conversationId}/members/{userId}")
    public ConversationMemberResponse updateMemberRole(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId,
        @PathVariable Long userId,
        @Valid @RequestBody UpdateConversationMemberRoleRequest request
    ) {
        ConversationMember member = conversationService.updateMemberRole(
            authenticatedUser,
            conversationId,
            userId,
            request.role()
        );
        return ConversationMemberResponse.from(member, null, false, userAccountService);
    }

    private ConversationResponse toConversationResponse(ConversationService.ConversationSummary summary) {
        ConversationMember membership = summary.membership();
        Conversation conversation = membership.getConversation();
        return new ConversationResponse(
            conversation.getId(),
            conversation.getTenantKey(),
            conversation.getTitle(),
            conversationService.buildConversationAvatarUrl(conversation),
            membership.getRole().name(),
            conversation.getCreatedAt(),
            summary.lastMessagePreview(),
            summary.lastMessageCreatedAt(),
            summary.unreadCount(),
            membership.isMuted()
        );
    }

    private ConversationService.AvatarUpload toConversationAvatarUpload(MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            return null;
        }

        try {
            return new ConversationService.AvatarUpload(
                avatar.getOriginalFilename(),
                avatar.getContentType(),
                avatar.getBytes()
            );
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось прочитать аватар чата");
        }
    }

    private List<ConversationAttachmentDraft> toAttachmentDrafts(
        List<MultipartFile> files,
        boolean sendAsVideoNote,
        boolean sendAsVoice
    ) {
        List<ConversationAttachmentDraft> drafts = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            return drafts;
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            try {
                drafts.add(new ConversationAttachmentDraft(
                    detectAttachmentKind(file.getContentType(), file.getOriginalFilename()),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    file.getBytes()
                ));
            } catch (Exception ex) {
                throw new IllegalArgumentException("Failed to read uploaded attachment");
            }
        }

        if (sendAsVoice && drafts.size() == 1) {
            ConversationAttachmentDraft draft = drafts.getFirst();
            drafts.set(0, new ConversationAttachmentDraft(
                ConversationAttachmentKind.VOICE,
                draft.originalFilename(),
                draft.mimeType(),
                draft.sizeBytes(),
                draft.content()
            ));
        } else if (sendAsVideoNote && drafts.size() == 1) {
            ConversationAttachmentDraft draft = drafts.getFirst();
            if (draft.kind() == ConversationAttachmentKind.VIDEO) {
                drafts.set(0, new ConversationAttachmentDraft(
                    ConversationAttachmentKind.VIDEO_NOTE,
                    draft.originalFilename(),
                    draft.mimeType(),
                    draft.sizeBytes(),
                    draft.content()
                ));
            }
        }

        return drafts;
    }

    private ConversationAttachmentKind detectAttachmentKind(String contentType, String originalFilename) {
        String normalizedContentType = contentType == null ? "" : contentType.trim().toLowerCase();
        if (normalizedContentType.startsWith("image/")) {
            return ConversationAttachmentKind.PHOTO;
        }
        if (normalizedContentType.startsWith("video/")) {
            return ConversationAttachmentKind.VIDEO;
        }
        if (normalizedContentType.startsWith("audio/")) {
            return ConversationAttachmentKind.VOICE;
        }

        String normalizedFilename = originalFilename == null ? "" : originalFilename.trim().toLowerCase();
        if (normalizedFilename.endsWith(".jpg")
            || normalizedFilename.endsWith(".jpeg")
            || normalizedFilename.endsWith(".png")
            || normalizedFilename.endsWith(".gif")
            || normalizedFilename.endsWith(".webp")
            || normalizedFilename.endsWith(".bmp")
            || normalizedFilename.endsWith(".heic")
            || normalizedFilename.endsWith(".heif")) {
            return ConversationAttachmentKind.PHOTO;
        }
        if (normalizedFilename.endsWith(".mp4")
            || normalizedFilename.endsWith(".mov")
            || normalizedFilename.endsWith(".m4v")
            || normalizedFilename.endsWith(".webm")
            || normalizedFilename.endsWith(".mkv")
            || normalizedFilename.endsWith(".avi")) {
            return ConversationAttachmentKind.VIDEO;
        }
        if (normalizedFilename.endsWith(".ogg")
            || normalizedFilename.endsWith(".oga")
            || normalizedFilename.endsWith(".opus")
            || normalizedFilename.endsWith(".mp3")
            || normalizedFilename.endsWith(".m4a")
            || normalizedFilename.endsWith(".aac")
            || normalizedFilename.endsWith(".wav")
            || normalizedFilename.endsWith(".webm")) {
            return ConversationAttachmentKind.VOICE;
        }

        return ConversationAttachmentKind.DOCUMENT;
    }

    private MediaType resolveMediaType(String rawMimeType) {
        if (rawMimeType == null || rawMimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        String sanitized = rawMimeType.trim().toLowerCase(Locale.ROOT);
        int parameterDelimiter = sanitized.indexOf(';');
        if (parameterDelimiter >= 0) {
            sanitized = sanitized.substring(0, parameterDelimiter).trim();
        }

        if (sanitized.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(sanitized);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

record CreateConversationRequest(
    @NotBlank(message = "title is required")
    String title
) {
}

record CreateConversationMessageRequest(
    @NotBlank(message = "body is required")
    String body,
    Long replyToMessageId
) {
}

record UpdateConversationMemberRoleRequest(
    @NotNull(message = "role is required")
    ConversationMemberRole role
) {
}

record UpdateConversationPreferencesRequest(
    boolean muted
) {
}

record ConversationResponse(
    Long id,
    String tenantKey,
    String title,
    String avatarUrl,
    String membershipRole,
    Instant createdAt,
    String lastMessagePreview,
    Instant lastMessageCreatedAt,
    long unreadCount,
    boolean muted
) {
}

record ConversationMessageResponse(
    Long id,
    Long conversationId,
    String sourceTransport,
    Long authorUserId,
    String authorExternalId,
    String authorDisplayName,
    String body,
    ConversationReplyResponse replyTo,
    List<ConversationAttachmentResponse> attachments,
    Instant createdAt
) {

    static ConversationMessageResponse from(
        ConversationMessage message,
        ConversationAttachmentService conversationAttachmentService
    ) {
        return new ConversationMessageResponse(
            message.getId(),
            message.getConversation().getId(),
            message.getSourceTransport().name(),
            message.getAuthorUser() == null ? null : message.getAuthorUser().getId(),
            message.getAuthorExternalId(),
            message.getAuthorDisplayName(),
            message.getBody(),
            ConversationReplyResponse.from(message.getReplyToMessage()),
            message.getAttachments().stream()
                .map((attachment) -> ConversationAttachmentResponse.from(attachment, conversationAttachmentService))
                .toList(),
            message.getCreatedAt()
        );
    }
}

record ConversationReplyResponse(
    Long id,
    Long authorUserId,
    String authorDisplayName,
    String body,
    List<ConversationReplyAttachmentResponse> attachments,
    Instant createdAt
) {

    static ConversationReplyResponse from(ConversationMessage message) {
        if (message == null) {
            return null;
        }

        return new ConversationReplyResponse(
            message.getId(),
            message.getAuthorUser() == null ? null : message.getAuthorUser().getId(),
            message.getAuthorDisplayName(),
            message.getBody(),
            message.getAttachments().stream()
                .map(ConversationReplyAttachmentResponse::from)
                .toList(),
            message.getCreatedAt()
        );
    }
}

record ConversationReplyAttachmentResponse(
    String kind,
    String fileName
) {

    static ConversationReplyAttachmentResponse from(ConversationAttachment attachment) {
        return new ConversationReplyAttachmentResponse(
            attachment.getKind().name(),
            attachment.getOriginalFilename()
        );
    }
}

record ConversationAttachmentResponse(
    Long id,
    String kind,
    String fileName,
    String mimeType,
    long sizeBytes,
    String contentUrl,
    String cacheKey
) {

    static ConversationAttachmentResponse from(
        ConversationAttachment attachment,
        ConversationAttachmentService conversationAttachmentService
    ) {
        return new ConversationAttachmentResponse(
            attachment.getId(),
            attachment.getKind().name(),
            attachment.getOriginalFilename(),
            attachment.getMimeType(),
            attachment.getSizeBytes(),
            conversationAttachmentService.buildContentUrl(attachment),
            attachment.getCreatedAt() == null
                ? String.valueOf(attachment.getId())
                : String.valueOf(attachment.getCreatedAt().toEpochMilli())
        );
    }
}

record ConversationMemberResponse(
    Long userId,
    String username,
    String displayName,
    String avatarUrl,
    String role,
    Instant joinedAt,
    boolean telegramLinked,
    String telegramUsername,
    boolean online,
    Instant lastSeenAt
) {

    static ConversationMemberResponse from(
        ConversationMember member,
        TelegramIdentity telegramIdentity,
        boolean online,
        UserAccountService userAccountService
    ) {
        return new ConversationMemberResponse(
            member.getUserAccount().getId(),
            member.getUserAccount().getUsername(),
            member.getUserAccount().getDisplayName(),
            userAccountService.buildAvatarUrl(member.getUserAccount()),
            member.getRole().name(),
            member.getJoinedAt(),
            telegramIdentity != null,
            telegramIdentity == null ? null : telegramIdentity.getTelegramUsername(),
            online,
            telegramIdentity == null ? null : telegramIdentity.getLastSeenAt()
        );
    }
}

record ConversationInviteResponse(
    Long id,
    Long conversationId,
    String inviteCode,
    Instant createdAt,
    Instant expiresAt
) {

    static ConversationInviteResponse from(IssuedConversationInvite issuedInvite) {
        return new ConversationInviteResponse(
            issuedInvite.invite().getId(),
            issuedInvite.invite().getConversation().getId(),
            issuedInvite.inviteCode(),
            issuedInvite.invite().getCreatedAt(),
            issuedInvite.invite().getExpiresAt()
        );
    }
}
