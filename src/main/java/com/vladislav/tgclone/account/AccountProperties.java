package com.vladislav.tgclone.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AccountProperties(
    String defaultTenantKey,
    long apiTokenTtlDays,
    String masterToken,
    String masterTokenUsername,
    String masterTokenDisplayName
) {
}
