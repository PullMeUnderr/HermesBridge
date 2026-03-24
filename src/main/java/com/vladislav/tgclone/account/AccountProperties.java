package com.vladislav.tgclone.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AccountProperties(
    String defaultTenantKey,
    long apiTokenTtlDays,
    long accessTokenTtlMinutes,
    long refreshTokenTtlDays,
    String refreshCookieName,
    String masterToken,
    String masterTokenUsername,
    String masterTokenDisplayName
) {
}
