package com.vladislav.tgclone.account;

import java.time.Instant;

public record TelegramLinkResult(
    boolean newlyLinked,
    Long userId,
    String username,
    String displayName,
    String tenantKey,
    String plainTextToken,
    Instant tokenExpiresAt
) {
}
