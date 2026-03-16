package com.vladislav.tgclone.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelegramRegistrationService {

    private static final int MAX_USERNAME_LENGTH = 40;

    private final UserAccountRepository userAccountRepository;
    private final TelegramIdentityRepository telegramIdentityRepository;
    private final ApiTokenService apiTokenService;
    private final AccountProperties accountProperties;
    private final Clock clock;

    public TelegramRegistrationService(
        UserAccountRepository userAccountRepository,
        TelegramIdentityRepository telegramIdentityRepository,
        ApiTokenService apiTokenService,
        AccountProperties accountProperties,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.telegramIdentityRepository = telegramIdentityRepository;
        this.apiTokenService = apiTokenService;
        this.accountProperties = accountProperties;
        this.clock = clock;
    }

    @Transactional
    public TelegramRegistrationResult registerOrRefresh(
        String telegramUserId,
        String telegramUsername,
        String displayName,
        String privateChatId
    ) {
        if (telegramUserId == null || telegramUserId.isBlank()) {
            throw new IllegalArgumentException("telegramUserId is required");
        }
        if (privateChatId == null || privateChatId.isBlank()) {
            throw new IllegalArgumentException("privateChatId is required");
        }

        Instant now = clock.instant();
        TelegramIdentity identity = telegramIdentityRepository.findByTelegramUserId(telegramUserId).orElse(null);
        boolean created = identity == null;

        UserAccount userAccount;
        if (created) {
            userAccount = new UserAccount(
                normalizeTenantKey(accountProperties.defaultTenantKey()),
                nextUsernameCandidate(telegramUsername, telegramUserId),
                normalizeDisplayName(displayName),
                true,
                now
            );
            userAccountRepository.save(userAccount);

            identity = new TelegramIdentity(
                userAccount,
                telegramUserId.trim(),
                normalizeNullable(telegramUsername),
                privateChatId.trim(),
                now,
                now
            );
            telegramIdentityRepository.save(identity);
        } else {
            userAccount = identity.getUserAccount();
            userAccount.updateDisplayName(normalizeDisplayName(displayName));
            identity.touch(normalizeNullable(telegramUsername), privateChatId.trim(), now);
        }

        IssuedApiToken apiToken = apiTokenService.rotateTelegramToken(userAccount);
        return new TelegramRegistrationResult(
            created,
            userAccount.getId(),
            userAccount.getUsername(),
            userAccount.getDisplayName(),
            userAccount.getTenantKey(),
            apiToken.plainTextToken(),
            apiToken.expiresAt()
        );
    }

    private String nextUsernameCandidate(String telegramUsername, String telegramUserId) {
        String baseUsername = sanitizeUsername(telegramUsername);
        if (baseUsername == null || baseUsername.isBlank()) {
            baseUsername = "telegram_" + telegramUserId.trim();
        }
        baseUsername = truncate(baseUsername);

        String candidate = baseUsername;
        int suffix = 1;
        while (userAccountRepository.existsByUsername(candidate)) {
            String suffixValue = "_" + suffix++;
            candidate = truncate(baseUsername, suffixValue.length()) + suffixValue;
        }
        return candidate;
    }

    private String sanitizeUsername(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+", "")
            .replaceAll("_+$", "");

        if (normalized.isBlank()) {
            return null;
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = "tg_" + normalized;
        }
        return normalized;
    }

    private String truncate(String value) {
        return truncate(value, 0);
    }

    private String truncate(String value, int suffixLength) {
        int maxLength = Math.max(1, MAX_USERNAME_LENGTH - suffixLength);
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return "Telegram User";
        }
        return value.trim();
    }

    private String normalizeTenantKey(String value) {
        if (value == null || value.isBlank()) {
            return "main";
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
