package com.vladislav.tgclone.conversation;

import java.util.List;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConversationEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationAttachmentService conversationAttachmentService;
    private final ConversationService conversationService;

    public ConversationEventPublisher(
        SimpMessagingTemplate messagingTemplate,
        ConversationAttachmentService conversationAttachmentService,
        ConversationService conversationService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.conversationAttachmentService = conversationAttachmentService;
        this.conversationService = conversationService;
    }

    public void publish(ConversationMessage message) {
        messagingTemplate.convertAndSend(
            "/topic/conversations/" + message.getConversation().getId(),
            ConversationSocketEvent.newMessage(message, conversationAttachmentService)
        );
        publishConversationSummaries(message.getConversation().getId());
    }

    public void publishConversationSummaries(Long conversationId) {
        List<ConversationService.ConversationSummary> summaries =
            conversationService.listConversationSummariesForConversation(conversationId);
        for (ConversationService.ConversationSummary summary : summaries) {
            ConversationSocketSummaryPayload payload = ConversationSocketSummaryPayload.from(summary, conversationService);
            messagingTemplate.convertAndSend(
                "/topic/users/" + summary.membership().getUserAccount().getId() + "/conversations",
                ConversationSocketEvent.conversationSummary(payload)
            );
        }
    }

    public void publishTyping(Long conversationId, Long userId, String displayName, boolean active) {
        messagingTemplate.convertAndSend(
            "/topic/conversations/" + conversationId,
            ConversationSocketEvent.typing(new ConversationTypingPayload(conversationId, userId, displayName, active))
        );
    }

    public void publishRead(ConversationReadPayload payload) {
        messagingTemplate.convertAndSend(
            "/topic/conversations/" + payload.conversationId(),
            ConversationSocketEvent.conversationRead(payload)
        );
    }

}
