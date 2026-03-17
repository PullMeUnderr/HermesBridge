package com.vladislav.tgclone.account;

import java.time.Instant;

public record TelegramRegistrationResult(
    boolean created,
    Long userId,
    String username,
    String displayName,
    String tenantKey,
    String plainTextToken,
    Instant tokenExpiresAt,
    boolean tokenCreatedNew
) {
}
