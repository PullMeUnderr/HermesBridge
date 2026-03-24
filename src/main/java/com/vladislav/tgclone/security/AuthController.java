package com.vladislav.tgclone.security;

import com.vladislav.tgclone.account.AccountProperties;
import com.vladislav.tgclone.account.AccountIdentityService;
import com.vladislav.tgclone.account.ApiTokenService;
import com.vladislav.tgclone.account.AuthSessionTokens;
import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.account.TelegramIdentity;
import com.vladislav.tgclone.account.TelegramLinkService;
import com.vladislav.tgclone.account.TelegramLinkToken;
import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.tdlight.connection.TdlightAccountBindingService;
import com.vladislav.tgclone.common.NotFoundException;
import java.io.IOException;
import com.vladislav.tgclone.media.MediaStorageService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ApiTokenService apiTokenService;
    private final AccountIdentityService accountIdentityService;
    private final TelegramLinkService telegramLinkService;
    private final AccountProperties accountProperties;
    private final UserAccountService userAccountService;
    private final MediaStorageService mediaStorageService;
    private final TdlightAccountBindingService tdlightAccountBindingService;

    public AuthController(
        ApiTokenService apiTokenService,
        AccountIdentityService accountIdentityService,
        TelegramLinkService telegramLinkService,
        AccountProperties accountProperties,
        UserAccountService userAccountService,
        MediaStorageService mediaStorageService,
        TdlightAccountBindingService tdlightAccountBindingService
    ) {
        this.apiTokenService = apiTokenService;
        this.accountIdentityService = accountIdentityService;
        this.telegramLinkService = telegramLinkService;
        this.accountProperties = accountProperties;
        this.userAccountService = userAccountService;
        this.mediaStorageService = mediaStorageService;
        this.tdlightAccountBindingService = tdlightAccountBindingService;
    }

    @PostMapping("/session")
    public ResponseEntity<AuthSessionResponse> createSession(
        @RequestBody AuthTokenExchangeRequest request,
        HttpServletRequest httpRequest
    ) {
        boolean allowMasterToken = isLoopbackRequest(httpRequest);
        AuthSessionTokens sessionTokens = apiTokenService.exchangeBootstrapToken(request.token(), allowMasterToken);
        return buildSessionResponse(sessionTokens, httpRequest);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthSessionResponse> register(
        @Valid @RequestBody HermesRegisterRequest request,
        HttpServletRequest httpRequest
    ) {
        UserAccount userAccount = accountIdentityService.registerHermesAccount(
            request.username(),
            request.displayName(),
            request.password()
        );
        return buildSessionResponse(apiTokenService.issueSessionForUser(userAccount), httpRequest);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(
        @Valid @RequestBody HermesLoginRequest request,
        HttpServletRequest httpRequest
    ) {
        UserAccount userAccount = accountIdentityService.authenticateHermesAccount(
            request.username(),
            request.password()
        );
        return buildSessionResponse(apiTokenService.issueSessionForUser(userAccount), httpRequest);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthRefreshResponse> refreshSession(
        HttpServletRequest request
    ) {
        String refreshToken = readRefreshCookie(request);
        AuthSessionTokens sessionTokens = apiTokenService.refreshSession(refreshToken)
            .orElseThrow(() -> new IllegalArgumentException("Refresh token is invalid"));

        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                buildRefreshCookie(sessionTokens.refreshToken(), sessionTokens.refreshTokenExpiresAt(), request.isSecure()).toString()
            )
            .body(new AuthRefreshResponse(sessionTokens.accessToken(), sessionTokens.accessTokenExpiresAt()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        HttpServletRequest request
    ) {
        String refreshToken = readRefreshCookie(request);
        apiTokenService.revokeSessionByRefreshToken(refreshToken);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, clearRefreshCookie(request.isSecure()).toString())
            .build();
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        UserAccount userAccount = userAccountService.requireActiveUser(authenticatedUser.userId());
        TelegramIdentity telegramIdentity = userAccountService.findTelegramIdentityByUserId(authenticatedUser.userId())
            .orElse(null);

        return toResponse(userAccount, telegramIdentity);
    }

    @PostMapping("/link/telegram/start")
    public TelegramLinkStartResponse startTelegramLink(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        TelegramLinkToken linkToken = telegramLinkService.createTelegramLinkToken(authenticatedUser);
        return new TelegramLinkStartResponse(linkToken.code(), linkToken.expiresAt());
    }

    @PostMapping("/me/complete-registration")
    public AuthenticatedUserResponse completeRegistration(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody HermesCompleteRegistrationRequest request
    ) {
        UserAccount updatedUser = accountIdentityService.completeHermesSetup(
            authenticatedUser,
            request.username(),
            request.displayName(),
            request.password()
        );
        TelegramIdentity telegramIdentity = userAccountService.findTelegramIdentityByUserId(authenticatedUser.userId())
            .orElse(null);
        return toResponse(updatedUser, telegramIdentity);
    }

    @PatchMapping(path = "/me/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AuthenticatedUserResponse updateProfile(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @RequestParam String displayName,
        @RequestParam String username,
        @RequestParam(name = "avatar", required = false) MultipartFile avatar,
        @RequestParam(name = "removeAvatar", defaultValue = "false") boolean removeAvatar
    ) {
        UserAccountService.AvatarUpload avatarUpload = toAvatarUpload(avatar);
        UserAccount updatedUser = userAccountService.updateProfile(
            authenticatedUser,
            displayName,
            username,
            avatarUpload,
            removeAvatar
        );
        TelegramIdentity telegramIdentity = userAccountService.findTelegramIdentityByUserId(authenticatedUser.userId())
            .orElse(null);
        return toResponse(updatedUser, telegramIdentity);
    }

    @GetMapping("/me/avatar")
    public ResponseEntity<InputStreamResource> myAvatar(@AuthenticationPrincipal AuthenticatedUser authenticatedUser)
        throws IOException {
        UserAccountService.ResolvedUserAvatar resolvedUserAvatar = userAccountService.resolveOwnAvatar(authenticatedUser);
        return avatarResponse(resolvedUserAvatar.userAccount());
    }

    @GetMapping("/users/{userId}/avatar")
    public ResponseEntity<InputStreamResource> userAvatar(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @org.springframework.web.bind.annotation.PathVariable Long userId
    ) throws IOException {
        UserAccountService.ResolvedUserAvatar resolvedUserAvatar =
            userAccountService.resolveAvatarForViewer(authenticatedUser, userId);
        return avatarResponse(resolvedUserAvatar.userAccount());
    }

    private ResponseEntity<InputStreamResource> avatarResponse(UserAccount userAccount) throws IOException {
        InputStreamResource body;
        try {
            body = new InputStreamResource(mediaStorageService.openStream(userAccount.getAvatarStorageKey()));
        } catch (IllegalStateException ex) {
            throw new NotFoundException("Avatar not found");
        }

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().mustRevalidate())
            .contentType(resolveMediaType(userAccount.getAvatarMimeType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(userAccount.getAvatarOriginalFilename()).build().toString()
            )
            .header(HttpHeaders.PRAGMA, "no-cache")
            .header(HttpHeaders.EXPIRES, "0")
            .body(body);
    }

    private ResponseCookie buildRefreshCookie(String refreshToken, java.time.Instant expiresAt, boolean secure) {
        long maxAgeSeconds = expiresAt == null ? -1 : Math.max(0, java.time.Duration.between(java.time.Instant.now(), expiresAt).getSeconds());
        return ResponseCookie.from(accountProperties.refreshCookieName(), refreshToken)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(maxAgeSeconds)
            .build();
    }

    private ResponseCookie clearRefreshCookie(boolean secure) {
        return ResponseCookie.from(accountProperties.refreshCookieName(), "")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(0)
            .build();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (accountProperties.refreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean isLoopbackRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        return isLoopbackHost(request.getRemoteAddr())
            && isLoopbackHost(request.getLocalAddr())
            && isLoopbackOrigin(request.getHeader(HttpHeaders.ORIGIN));
    }

    private boolean isLoopbackOrigin(String originHeader) {
        if (originHeader == null || originHeader.isBlank()) {
            return true;
        }

        try {
            java.net.URI origin = java.net.URI.create(originHeader.trim());
            return isLoopbackHost(origin.getHost());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isLoopbackHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }

        String normalized = host.trim();
        return "127.0.0.1".equals(normalized)
            || "0:0:0:0:0:0:0:1".equals(normalized)
            || "::1".equals(normalized)
            || "localhost".equalsIgnoreCase(normalized);
    }

    private AuthenticatedUserResponse toResponse(UserAccount userAccount, TelegramIdentity telegramIdentity) {
        TdlightAccountBindingService.TdlightBoundAccount tdlightBoundAccount = telegramIdentity == null
            ? tdlightAccountBindingService.findBoundAccount(userAccount).orElse(null)
            : null;
        return new AuthenticatedUserResponse(
            userAccount.getId(),
            userAccount.getTenantKey(),
            userAccount.getUsername(),
            userAccount.getDisplayName(),
            userAccountService.buildOwnAvatarUrl(userAccount),
            accountIdentityService.hasPasswordIdentity(userAccount.getId()),
            telegramIdentity != null || tdlightBoundAccount != null,
            telegramIdentity == null
                ? tdlightBoundAccount == null ? null : tdlightBoundAccount.telegramUserId()
                : telegramIdentity.getTelegramUserId(),
            telegramIdentity == null
                ? tdlightBoundAccount == null ? null : tdlightBoundAccount.telegramUsername()
                : telegramIdentity.getTelegramUsername(),
            telegramIdentity == null
                ? tdlightBoundAccount != null
                : userAccountService.isOnline(telegramIdentity),
            telegramIdentity == null
                ? tdlightBoundAccount == null ? null : tdlightBoundAccount.verifiedAt()
                : telegramIdentity.getLastSeenAt()
        );
    }

    private ResponseEntity<AuthSessionResponse> buildSessionResponse(
        AuthSessionTokens sessionTokens,
        HttpServletRequest httpRequest
    ) {
        AuthenticatedUserResponse user = me(apiTokenService.authenticateAccessToken(sessionTokens.accessToken())
            .orElseThrow(() -> new IllegalStateException("Issued access token is invalid")));

        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                buildRefreshCookie(sessionTokens.refreshToken(), sessionTokens.refreshTokenExpiresAt(), httpRequest.isSecure()).toString()
            )
            .body(new AuthSessionResponse(sessionTokens.accessToken(), sessionTokens.accessTokenExpiresAt(), user));
    }

    private UserAccountService.AvatarUpload toAvatarUpload(MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            return null;
        }

        try {
            return new UserAccountService.AvatarUpload(
                avatar.getOriginalFilename(),
                avatar.getContentType(),
                avatar.getBytes()
            );
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось прочитать файл аватара");
        }
    }

    private MediaType resolveMediaType(String rawMimeType) {
        if (rawMimeType == null || rawMimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        String sanitized = rawMimeType.trim();
        int parameterDelimiter = sanitized.indexOf(';');
        if (parameterDelimiter >= 0) {
            sanitized = sanitized.substring(0, parameterDelimiter).trim();
        }

        if (sanitized.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(sanitized);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

record AuthTokenExchangeRequest(String token) {
}

record AuthSessionResponse(
    String accessToken,
    java.time.Instant accessTokenExpiresAt,
    AuthenticatedUserResponse user
) {
}

record AuthRefreshResponse(
    String accessToken,
    java.time.Instant accessTokenExpiresAt
) {
}

record AuthenticatedUserResponse(
    Long id,
    String tenantKey,
    String username,
    String displayName,
    String avatarUrl,
    boolean passwordLinked,
    boolean telegramLinked,
    String telegramUserId,
    String telegramUsername,
    boolean online,
    java.time.Instant lastSeenAt
) {
}

record HermesRegisterRequest(
    @NotBlank String username,
    @NotBlank String displayName,
    @NotBlank String password
) {
}

record HermesLoginRequest(
    @NotBlank String username,
    @NotBlank String password
) {
}

record TelegramLinkStartResponse(
    String code,
    java.time.Instant expiresAt
) {
}

record HermesCompleteRegistrationRequest(
    @NotBlank String username,
    @NotBlank String displayName,
    @NotBlank String password
) {
}
