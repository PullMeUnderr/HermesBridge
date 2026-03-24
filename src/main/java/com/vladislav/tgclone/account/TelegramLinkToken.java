package com.vladislav.tgclone.account;

import java.time.Instant;

public record TelegramLinkToken(
    String code,
    Instant expiresAt
) {
}
