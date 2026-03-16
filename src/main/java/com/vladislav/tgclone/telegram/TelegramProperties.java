package com.vladislav.tgclone.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
    boolean enabled,
    String botToken,
    String botUsername,
    int pollTimeoutSeconds
) {
}
