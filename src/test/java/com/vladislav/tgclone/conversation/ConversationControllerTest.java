package com.vladislav.tgclone.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ConversationControllerTest {

    private final ConversationController conversationController = new ConversationController(null, null, null, null, null);

    @Test
    void detectAttachmentKindFallsBackToVideoExtension() {
        ConversationAttachmentKind kind = ReflectionTestUtils.invokeMethod(
            conversationController,
            "detectAttachmentKind",
            "application/octet-stream",
            "movie.mp4"
        );

        assertEquals(ConversationAttachmentKind.VIDEO, kind);
    }

    @Test
    void detectAttachmentKindFallsBackToVoiceExtension() {
        ConversationAttachmentKind kind = ReflectionTestUtils.invokeMethod(
            conversationController,
            "detectAttachmentKind",
            "application/octet-stream",
            "voice.m4a"
        );

        assertEquals(ConversationAttachmentKind.VOICE, kind);
    }
}
