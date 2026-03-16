package com.vladislav.tgclone.conversation;

public record ConversationInviteAcceptanceResult(
    ConversationMember membership,
    boolean alreadyMember
) {
}
