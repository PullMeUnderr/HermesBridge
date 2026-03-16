package com.vladislav.tgclone.conversation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.conversations")
public record ConversationProperties(int inviteTtlHours) {

    public ConversationProperties {
        if (inviteTtlHours <= 0) {
            inviteTtlHours = 168;
        }
    }
}
