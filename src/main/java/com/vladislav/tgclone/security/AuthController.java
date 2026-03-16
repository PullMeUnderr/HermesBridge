package com.vladislav.tgclone.security;

import com.vladislav.tgclone.account.TelegramIdentity;
import com.vladislav.tgclone.account.UserAccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountService userAccountService;

    public AuthController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        TelegramIdentity telegramIdentity = userAccountService.findTelegramIdentityByUserId(authenticatedUser.userId())
            .orElse(null);

        return new AuthenticatedUserResponse(
            authenticatedUser.userId(),
            authenticatedUser.tenantKey(),
            authenticatedUser.username(),
            authenticatedUser.displayName(),
            telegramIdentity != null,
            telegramIdentity == null ? null : telegramIdentity.getTelegramUserId(),
            telegramIdentity == null ? null : telegramIdentity.getTelegramUsername()
        );
    }
}

record AuthenticatedUserResponse(
    Long id,
    String tenantKey,
    String username,
    String displayName,
    boolean telegramLinked,
    String telegramUserId,
    String telegramUsername
) {
}
