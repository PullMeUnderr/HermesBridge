package com.vladislav.tgclone.conversation;

public record IssuedConversationInvite(
    ConversationInvite invite,
    String inviteCode
) {
}
