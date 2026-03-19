package com.vladislav.tgclone.conversation;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConversationEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationAttachmentService conversationAttachmentService;

    public ConversationEventPublisher(
        SimpMessagingTemplate messagingTemplate,
        ConversationAttachmentService conversationAttachmentService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.conversationAttachmentService = conversationAttachmentService;
    }

    public void publish(ConversationMessage message) {
        messagingTemplate.convertAndSend(
            "/topic/conversations/" + message.getConversation().getId(),
            ConversationMessageResponse.from(message, conversationAttachmentService)
        );
    }
}
