package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.bridge.MessageRelayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    public ConversationController(
        ConversationService conversationService,
        MessageRelayService messageRelayService
    ) {
        this.conversationService = conversationService;
        this.messageRelayService = messageRelayService;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody CreateConversationRequest request
    ) {
        ConversationMember membership = conversationService.createConversation(authenticatedUser, request.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversationResponse.from(membership));
    }

    @GetMapping
    public List<ConversationResponse> listConversations(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return conversationService.listMemberships(authenticatedUser).stream()
            .map(ConversationResponse::from)
            .toList();
    }

    @GetMapping("/{conversationId}")
    public ConversationResponse getConversation(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId
    ) {
        return ConversationResponse.from(conversationService.requireMembership(authenticatedUser, conversationId));
    }

    @GetMapping("/{conversationId}/messages")
    public List<ConversationMessageResponse> listMessages(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId
    ) {
        return conversationService.listMessages(authenticatedUser, conversationId).stream()
            .map(ConversationMessageResponse::from)
            .toList();
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
            request.body()
        );
        messageRelayService.relayInternalMessage(message);
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversationMessageResponse.from(message));
    }

    @PostMapping(
        path = "/{conversationId}/messages/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ConversationMessageResponse> uploadMessage(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId,
        @RequestParam(required = false) String body,
        @RequestParam(name = "files", required = false) List<MultipartFile> files
    ) {
        List<ConversationAttachmentDraft> attachments = toAttachmentDrafts(files);
        ConversationMessage message = conversationService.createInternalMessage(
            authenticatedUser,
            conversationId,
            body,
            attachments
        );
        messageRelayService.relayInternalMessage(message);
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversationMessageResponse.from(message));
    }

    @GetMapping("/{conversationId}/members")
    public List<ConversationMemberResponse> listMembers(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long conversationId
    ) {
        return conversationService.listMembers(authenticatedUser, conversationId).stream()
            .map(ConversationMemberResponse::from)
            .toList();
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
        return ConversationMemberResponse.from(member);
    }

    private List<ConversationAttachmentDraft> toAttachmentDrafts(List<MultipartFile> files) {
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
}

record CreateConversationRequest(
    @NotBlank(message = "title is required")
    String title
) {
}

record CreateConversationMessageRequest(
    @NotBlank(message = "body is required")
    String body
) {
}

record UpdateConversationMemberRoleRequest(
    @NotNull(message = "role is required")
    ConversationMemberRole role
) {
}

record ConversationResponse(
    Long id,
    String tenantKey,
    String title,
    String membershipRole,
    Instant createdAt
) {

    static ConversationResponse from(ConversationMember membership) {
        Conversation conversation = membership.getConversation();
        return new ConversationResponse(
            conversation.getId(),
            conversation.getTenantKey(),
            conversation.getTitle(),
            membership.getRole().name(),
            conversation.getCreatedAt()
        );
    }
}

record ConversationMessageResponse(
    Long id,
    Long conversationId,
    String sourceTransport,
    Long authorUserId,
    String authorExternalId,
    String authorDisplayName,
    String body,
    List<ConversationAttachmentResponse> attachments,
    Instant createdAt
) {

    static ConversationMessageResponse from(ConversationMessage message) {
        return new ConversationMessageResponse(
            message.getId(),
            message.getConversation().getId(),
            message.getSourceTransport().name(),
            message.getAuthorUser() == null ? null : message.getAuthorUser().getId(),
            message.getAuthorExternalId(),
            message.getAuthorDisplayName(),
            message.getBody(),
            message.getAttachments().stream()
                .map(ConversationAttachmentResponse::from)
                .toList(),
            message.getCreatedAt()
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

    static ConversationAttachmentResponse from(ConversationAttachment attachment) {
        String version = attachment.getCreatedAt() == null
            ? String.valueOf(attachment.getId())
            : String.valueOf(attachment.getCreatedAt().toEpochMilli());
        return new ConversationAttachmentResponse(
            attachment.getId(),
            attachment.getKind().name(),
            attachment.getOriginalFilename(),
            attachment.getMimeType(),
            attachment.getSizeBytes(),
            "/api/attachments/" + attachment.getId() + "/content?v=" + version,
            version
        );
    }
}

record ConversationMemberResponse(
    Long userId,
    String username,
    String displayName,
    String role,
    Instant joinedAt
) {

    static ConversationMemberResponse from(ConversationMember member) {
        return new ConversationMemberResponse(
            member.getUserAccount().getId(),
            member.getUserAccount().getUsername(),
            member.getUserAccount().getDisplayName(),
            member.getRole().name(),
            member.getJoinedAt()
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
