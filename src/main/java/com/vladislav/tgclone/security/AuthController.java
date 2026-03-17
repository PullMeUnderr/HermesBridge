package com.vladislav.tgclone.security;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.account.TelegramIdentity;
import com.vladislav.tgclone.account.UserAccountService;
import java.io.IOException;
import com.vladislav.tgclone.media.MediaStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountService userAccountService;
    private final MediaStorageService mediaStorageService;

    public AuthController(UserAccountService userAccountService, MediaStorageService mediaStorageService) {
        this.userAccountService = userAccountService;
        this.mediaStorageService = mediaStorageService;
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        UserAccount userAccount = userAccountService.requireActiveUser(authenticatedUser.userId());
        TelegramIdentity telegramIdentity = userAccountService.findTelegramIdentityByUserId(authenticatedUser.userId())
            .orElse(null);

        return toResponse(userAccount, telegramIdentity);
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
        UserAccount userAccount = resolvedUserAvatar.userAccount();

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().mustRevalidate())
            .contentType(resolveMediaType(userAccount.getAvatarMimeType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(userAccount.getAvatarOriginalFilename()).build().toString()
            )
            .header(HttpHeaders.PRAGMA, "no-cache")
            .header(HttpHeaders.EXPIRES, "0")
            .body(new InputStreamResource(mediaStorageService.openStream(userAccount.getAvatarStorageKey())));
    }

    private AuthenticatedUserResponse toResponse(UserAccount userAccount, TelegramIdentity telegramIdentity) {
        return new AuthenticatedUserResponse(
            userAccount.getId(),
            userAccount.getTenantKey(),
            userAccount.getUsername(),
            userAccount.getDisplayName(),
            userAccountService.buildOwnAvatarUrl(userAccount),
            telegramIdentity != null,
            telegramIdentity == null ? null : telegramIdentity.getTelegramUserId(),
            telegramIdentity == null ? null : telegramIdentity.getTelegramUsername()
        );
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

record AuthenticatedUserResponse(
    Long id,
    String tenantKey,
    String username,
    String displayName,
    String avatarUrl,
    boolean telegramLinked,
    String telegramUserId,
    String telegramUsername
) {
}
