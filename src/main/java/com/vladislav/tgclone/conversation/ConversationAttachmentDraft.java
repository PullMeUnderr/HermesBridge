package com.vladislav.tgclone.conversation;

public record ConversationAttachmentDraft(
    ConversationAttachmentKind kind,
    String originalFilename,
    String mimeType,
    long sizeBytes,
    byte[] content
) {
}
