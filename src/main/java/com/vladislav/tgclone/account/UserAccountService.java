package com.vladislav.tgclone.account;

import com.vladislav.tgclone.common.NotFoundException;
import com.vladislav.tgclone.media.MediaStorageService;
import com.vladislav.tgclone.media.StoredMediaFile;
import com.vladislav.tgclone.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private static final int MAX_USERNAME_LENGTH = 100;

    private final UserAccountRepository userAccountRepository;
    private final TelegramIdentityRepository telegramIdentityRepository;
    private final MediaStorageService mediaStorageService;
    private final Clock clock;

    public UserAccountService(
        UserAccountRepository userAccountRepository,
        TelegramIdentityRepository telegramIdentityRepository,
        MediaStorageService mediaStorageService,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.telegramIdentityRepository = telegramIdentityRepository;
        this.mediaStorageService = mediaStorageService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserAccount requireActiveUser(Long userId) {
        return userAccountRepository.findByIdAndActiveTrue(userId)
            .orElseThrow(() -> new NotFoundException("User %s not found".formatted(userId)));
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findByTelegramUserId(String telegramUserId) {
        if (telegramUserId == null || telegramUserId.isBlank()) {
            return Optional.empty();
        }
        return telegramIdentityRepository.findByTelegramUserId(telegramUserId)
            .map(TelegramIdentity::getUserAccount)
            .filter(UserAccount::isActive);
    }

    @Transactional(readOnly = true)
    public Optional<TelegramIdentity> findTelegramIdentityByUserId(Long userId) {
        return telegramIdentityRepository.findByUserAccount_Id(userId);
    }

    @Transactional(readOnly = true)
    public Map<Long, TelegramIdentity> findTelegramIdentitiesByUserIds(Collection<Long> userIds) {
        Map<Long, TelegramIdentity> identitiesByUserId = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return identitiesByUserId;
        }

        telegramIdentityRepository.findAllByUserAccount_IdIn(userIds)
            .forEach(identity -> identitiesByUserId.put(identity.getUserAccount().getId(), identity));
        return identitiesByUserId;
    }

    public boolean isOnline(TelegramIdentity telegramIdentity) {
        if (telegramIdentity == null || telegramIdentity.getLastSeenAt() == null) {
            return false;
        }
        return telegramIdentity.getLastSeenAt().isAfter(clock.instant().minus(5, ChronoUnit.MINUTES));
    }

    @Transactional
    public UserAccount updateProfile(
        AuthenticatedUser authenticatedUser,
        String displayName,
        String username,
        AvatarUpload avatarUpload,
        boolean removeAvatar
    ) {
        UserAccount userAccount = requireActiveUser(authenticatedUser.userId());

        String normalizedDisplayName = normalizeDisplayName(displayName);
        String normalizedUsername = normalizeUsername(username);
        if (userAccountRepository.existsByUsernameAndIdNot(normalizedUsername, userAccount.getId())) {
            throw new IllegalArgumentException("Этот ник уже занят");
        }

        userAccount.updateDisplayName(normalizedDisplayName);
        userAccount.updateUsername(normalizedUsername);

        if (avatarUpload != null && avatarUpload.content().length > 0) {
            validateAvatarUpload(avatarUpload);
            StoredMediaFile storedMediaFile = mediaStorageService.store(
                avatarUpload.originalFilename(),
                avatarUpload.mimeType(),
                avatarUpload.content()
            );
            userAccount.updateAvatar(
                storedMediaFile.storageKey(),
                storedMediaFile.mimeType(),
                storedMediaFile.originalFilename(),
                clock.instant()
            );
        } else if (removeAvatar) {
            userAccount.clearAvatar();
        }

        return userAccount;
    }

    @Transactional(readOnly = true)
    public ResolvedUserAvatar resolveOwnAvatar(AuthenticatedUser authenticatedUser) {
        UserAccount userAccount = requireActiveUser(authenticatedUser.userId());
        if (userAccount.getAvatarStorageKey() == null || userAccount.getAvatarStorageKey().isBlank()) {
            throw new NotFoundException("Avatar not found");
        }

        return new ResolvedUserAvatar(userAccount);
    }

    public String buildOwnAvatarUrl(UserAccount userAccount) {
        if (userAccount == null || userAccount.getAvatarStorageKey() == null || userAccount.getAvatarStorageKey().isBlank()) {
            return null;
        }

        Instant versionSource = userAccount.getAvatarUpdatedAt();
        String version = versionSource == null
            ? String.valueOf(userAccount.getId())
            : String.valueOf(versionSource.toEpochMilli());
        return "/api/auth/me/avatar?v=" + version;
    }

    public String buildAvatarUrl(UserAccount userAccount) {
        if (userAccount == null || userAccount.getId() == null) {
            return null;
        }
        return buildOwnAvatarUrl(userAccount);
    }

    private void validateAvatarUpload(AvatarUpload avatarUpload) {
        String mimeType = avatarUpload.mimeType() == null ? "" : avatarUpload.mimeType().trim().toLowerCase(Locale.ROOT);
        String fileName = avatarUpload.originalFilename() == null ? "" : avatarUpload.originalFilename().trim().toLowerCase(Locale.ROOT);

        boolean imageMime = mimeType.startsWith("image/");
        boolean imageExtension = fileName.endsWith(".jpg")
            || fileName.endsWith(".jpeg")
            || fileName.endsWith(".png")
            || fileName.endsWith(".gif")
            || fileName.endsWith(".webp")
            || fileName.endsWith(".bmp")
            || fileName.endsWith(".heic")
            || fileName.endsWith(".heif")
            || fileName.endsWith(".svg");

        if (!imageMime && !imageExtension) {
            throw new IllegalArgumentException("Аватар должен быть изображением");
        }
    }

    private String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Имя в Hermes обязательно");
        }

        String normalized = value.trim();
        if (normalized.length() > 255) {
            return normalized.substring(0, 255);
        }
        return normalized;
    }

    private String normalizeUsername(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Ник в Hermes обязателен");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+", "")
            .replaceAll("_+$", "");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Ник должен содержать латиницу, цифры или _");
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = "user_" + normalized;
        }
        if (normalized.length() > MAX_USERNAME_LENGTH) {
            normalized = normalized.substring(0, MAX_USERNAME_LENGTH);
        }
        return normalized;
    }

    public record AvatarUpload(
        String originalFilename,
        String mimeType,
        byte[] content
    ) {
    }

    public record ResolvedUserAvatar(
        UserAccount userAccount
    ) {
    }
}
