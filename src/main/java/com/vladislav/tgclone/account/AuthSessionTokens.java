package com.vladislav.tgclone.account;

import java.time.Instant;

public record AuthSessionTokens(
    String accessToken,
    Instant accessTokenExpiresAt,
    String refreshToken,
    Instant refreshTokenExpiresAt
) {
}
