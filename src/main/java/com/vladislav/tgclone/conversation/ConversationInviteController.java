package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.security.AuthenticatedUser;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invites")
public class ConversationInviteController {

    private final ConversationService conversationService;

    public ConversationInviteController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/{inviteCode}/accept")
    public ResponseEntity<ConversationInviteAcceptanceResponse> acceptInvite(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable String inviteCode
    ) {
        ConversationInviteAcceptanceResult result = conversationService.acceptInvite(authenticatedUser, inviteCode);
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversationInviteAcceptanceResponse.from(result));
    }
}

record ConversationInviteAcceptanceResponse(
    Long conversationId,
    String conversationTitle,
    String role,
    boolean alreadyMember,
    Instant joinedAt
) {

    static ConversationInviteAcceptanceResponse from(ConversationInviteAcceptanceResult result) {
        ConversationMember membership = result.membership();
        return new ConversationInviteAcceptanceResponse(
            membership.getConversation().getId(),
            membership.getConversation().getTitle(),
            membership.getRole().name(),
            result.alreadyMember(),
            membership.getJoinedAt()
        );
    }
}
