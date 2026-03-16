package com.vladislav.tgclone.account;

import java.time.Instant;

public record IssuedApiToken(
    String plainTextToken,
    Instant expiresAt
) {
}
