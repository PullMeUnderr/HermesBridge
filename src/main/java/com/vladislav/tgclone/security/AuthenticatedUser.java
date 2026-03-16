package com.vladislav.tgclone.security;

import com.vladislav.tgclone.account.UserAccount;

public record AuthenticatedUser(
    Long userId,
    String tenantKey,
    String username,
    String displayName
) {

    public static AuthenticatedUser from(UserAccount userAccount) {
        return new AuthenticatedUser(
            userAccount.getId(),
            userAccount.getTenantKey(),
            userAccount.getUsername(),
            userAccount.getDisplayName()
        );
    }
}
