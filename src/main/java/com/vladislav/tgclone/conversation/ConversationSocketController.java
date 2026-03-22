package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.security.AuthenticatedUser;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

@Controller
public class ConversationSocketController {

    private final ConversationService conversationService;
    private final ConversationEventPublisher conversationEventPublisher;

    public ConversationSocketController(
        ConversationService conversationService,
        ConversationEventPublisher conversationEventPublisher
    ) {
        this.conversationService = conversationService;
        this.conversationEventPublisher = conversationEventPublisher;
    }

    @MessageMapping("/conversations/{conversationId}/read")
    public void markConversationRead(
        @DestinationVariable Long conversationId,
        Principal principal
    ) {
        AuthenticatedUser authenticatedUser = extractAuthenticatedUser(principal);
        ConversationReadPayload payload = conversationService.markConversationRead(authenticatedUser, conversationId);
        if (payload != null) {
            conversationEventPublisher.publishRead(payload);
        }
        conversationEventPublisher.publishConversationSummaries(conversationId);
    }

    @MessageMapping("/conversations/{conversationId}/typing")
    public void updateTypingState(
        @DestinationVariable Long conversationId,
        @Payload ConversationTypingCommand command,
        Principal principal
    ) {
        AuthenticatedUser authenticatedUser = extractAuthenticatedUser(principal);
        conversationService.requireMembership(authenticatedUser, conversationId);
        conversationEventPublisher.publishTyping(
            conversationId,
            authenticatedUser.userId(),
            authenticatedUser.displayName(),
            command != null && command.active()
        );
    }

    private AuthenticatedUser extractAuthenticatedUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication
            && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }

        throw new AccessDeniedException("Unauthorized websocket message");
    }
}
