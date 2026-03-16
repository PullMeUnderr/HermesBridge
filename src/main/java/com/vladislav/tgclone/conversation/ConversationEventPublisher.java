package com.vladislav.tgclone.conversation;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConversationEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public ConversationEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(ConversationMessage message) {
        messagingTemplate.convertAndSend(
            "/topic/conversations/" + message.getConversation().getId(),
            ConversationMessageResponse.from(message)
        );
    }
}
