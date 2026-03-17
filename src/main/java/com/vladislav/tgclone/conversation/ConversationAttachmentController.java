package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.security.AuthenticatedUser;
import java.io.IOException;
import com.vladislav.tgclone.media.MediaStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attachments")
public class ConversationAttachmentController {

    private final ConversationAttachmentService conversationAttachmentService;
    private final MediaStorageService mediaStorageService;

    public ConversationAttachmentController(
        ConversationAttachmentService conversationAttachmentService,
        MediaStorageService mediaStorageService
    ) {
        this.conversationAttachmentService = conversationAttachmentService;
        this.mediaStorageService = mediaStorageService;
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<InputStreamResource> getAttachmentContent(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long attachmentId
    ) throws IOException {
        ConversationAttachmentService.ResolvedConversationAttachment resolvedAttachment =
            conversationAttachmentService.resolveForUser(authenticatedUser, attachmentId);

        ConversationAttachment attachment = resolvedAttachment.attachment();
        MediaType mediaType = resolveMediaType(attachment.getMimeType());

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().mustRevalidate())
            .contentType(mediaType)
            .contentLength(attachment.getSizeBytes())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(attachment.getOriginalFilename()).build().toString()
            )
            .header(HttpHeaders.PRAGMA, "no-cache")
            .header(HttpHeaders.EXPIRES, "0")
            .body(new InputStreamResource(mediaStorageService.openStream(attachment.getStorageKey())));
    }

    private MediaType resolveMediaType(String rawMimeType) {
        if (rawMimeType == null || rawMimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        String sanitized = rawMimeType.trim();
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
