package com.vladislav.tgclone.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import com.vladislav.tgclone.security.AuthenticatedUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountIdentityService {

    private static final int MAX_USERNAME_LENGTH = 100;
    private static final int MAX_DISPLAY_NAME_LENGTH = 255;

    private final UserAccountRepository userAccountRepository;
    private final AccountIdentityRepository accountIdentityRepository;
    private final TelegramIdentityRepository telegramIdentityRepository;
    private final AccountProperties accountProperties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountIdentityService(
        UserAccountRepository userAccountRepository,
        AccountIdentityRepository accountIdentityRepository,
        TelegramIdentityRepository telegramIdentityRepository,
        AccountProperties accountProperties,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.accountIdentityRepository = accountIdentityRepository;
        this.telegramIdentityRepository = telegramIdentityRepository;
        this.accountProperties = accountProperties;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public UserAccount registerHermesAccount(String username, String displayName, String password) {
        String normalizedUsername = normalizeUsername(username);
        if (userAccountRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Этот ник уже занят");
        }

        Instant now = clock.instant();
        UserAccount userAccount = userAccountRepository.save(new UserAccount(
            normalizeTenantKey(accountProperties.defaultTenantKey()),
            normalizedUsername,
            normalizeDisplayName(displayName),
            true,
            now
        ));

        accountIdentityRepository.save(new AccountIdentity(
            userAccount,
            AccountIdentityProvider.PASSWORD.value(),
            "password:" + UUID.randomUUID(),
            passwordEncoder.encode(normalizePassword(password)),
            null,
            null,
            now,
            now,
            now
        ));
        return userAccount;
    }

    @Transactional(readOnly = true)
    public UserAccount authenticateHermesAccount(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        UserAccount userAccount = userAccountRepository.findByUsername(normalizedUsername)
            .filter(UserAccount::isActive)
            .orElseThrow(() -> new IllegalArgumentException("Неверный логин или пароль"));

        AccountIdentity passwordIdentity = accountIdentityRepository
            .findByUserAccount_IdAndProvider(userAccount.getId(), AccountIdentityProvider.PASSWORD.value())
            .orElseThrow(() -> new IllegalArgumentException("Неверный логин или пароль"));

        if (!passwordEncoder.matches(normalizePassword(password), passwordIdentity.getSecretHash())) {
            throw new IllegalArgumentException("Неверный логин или пароль");
        }
        return userAccount;
    }

    @Transactional(readOnly = true)
    public boolean hasPasswordIdentity(Long userAccountId) {
        return accountIdentityRepository.existsByUserAccount_IdAndProvider(
            userAccountId,
            AccountIdentityProvider.PASSWORD.value()
        );
    }

    @Transactional
    public UserAccount completeHermesSetup(
        AuthenticatedUser authenticatedUser,
        String username,
        String displayName,
        String password
    ) {
        if (authenticatedUser == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        UserAccount userAccount = userAccountRepository.findByIdAndActiveTrue(authenticatedUser.userId())
            .orElseThrow(() -> new IllegalArgumentException("Аккаунт не найден"));
        if (hasPasswordIdentity(userAccount.getId())) {
            throw new IllegalArgumentException("Hermes login уже настроен для этого аккаунта");
        }

        String normalizedUsername = normalizeUsername(username);
        if (userAccountRepository.existsByUsernameAndIdNot(normalizedUsername, userAccount.getId())) {
            throw new IllegalArgumentException("Этот ник уже занят");
        }

        String normalizedDisplayName = normalizeDisplayName(displayName);
        String normalizedPassword = normalizePassword(password);
        Instant now = clock.instant();

        userAccount.updateUsername(normalizedUsername);
        userAccount.updateDisplayName(normalizedDisplayName);

        accountIdentityRepository.save(new AccountIdentity(
            userAccount,
            AccountIdentityProvider.PASSWORD.value(),
            "password:" + UUID.randomUUID(),
            passwordEncoder.encode(normalizedPassword),
            null,
            null,
            now,
            now,
            now
        ));
        return userAccount;
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findActiveUserByTelegramUserId(String telegramUserId) {
        if (telegramUserId == null || telegramUserId.isBlank()) {
            return Optional.empty();
        }

        return accountIdentityRepository.findByProviderAndProviderUserKey(
                AccountIdentityProvider.TELEGRAM.value(),
                telegramUserId.trim()
            )
            .map(AccountIdentity::getUserAccount)
            .filter(UserAccount::isActive);
    }

    @Transactional
    public void ensureTelegramIdentity(
        UserAccount userAccount,
        String telegramUserId,
        String telegramUsername,
        String privateChatId
    ) {
        if (userAccount == null) {
            throw new IllegalArgumentException("userAccount is required");
        }

        String normalizedTelegramUserId = normalizeRequired(telegramUserId, "telegramUserId is required");
        String normalizedPrivateChatId = normalizeRequired(privateChatId, "privateChatId is required");
        String normalizedTelegramUsername = normalizeNullable(telegramUsername);
        Instant now = clock.instant();

        AccountIdentity accountIdentity = accountIdentityRepository.findByProviderAndProviderUserKey(
            AccountIdentityProvider.TELEGRAM.value(),
            normalizedTelegramUserId
        ).orElse(null);
        if (accountIdentity == null) {
            accountIdentityRepository.save(new AccountIdentity(
                userAccount,
                AccountIdentityProvider.TELEGRAM.value(),
                normalizedTelegramUserId,
                null,
                normalizedTelegramUsername,
                normalizedPrivateChatId,
                now,
                now,
                now
            ));
        } else if (!accountIdentity.getUserAccount().getId().equals(userAccount.getId())) {
            throw new IllegalArgumentException("Этот Telegram уже привязан к другому Hermes account");
        } else {
            accountIdentity.touchExternalIdentity(normalizedTelegramUsername, normalizedPrivateChatId, now);
        }

        TelegramIdentity legacyIdentity = telegramIdentityRepository.findByTelegramUserId(normalizedTelegramUserId).orElse(null);
        if (legacyIdentity == null) {
            telegramIdentityRepository.save(new TelegramIdentity(
                userAccount,
                normalizedTelegramUserId,
                normalizedTelegramUsername,
                normalizedPrivateChatId,
                now,
                now
            ));
        } else if (!legacyIdentity.getUserAccount().getId().equals(userAccount.getId())) {
            throw new IllegalArgumentException("Legacy Telegram identity conflict");
        } else {
            legacyIdentity.touch(normalizedTelegramUsername, normalizedPrivateChatId, now);
        }
    }

    private String normalizePassword(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Пароль обязателен");
        }
        String normalized = password.trim();
        if (normalized.length() < 8) {
            throw new IllegalArgumentException("Пароль должен быть не короче 8 символов");
        }
        if (normalized.length() > 255) {
            throw new IllegalArgumentException("Пароль слишком длинный");
        }
        return normalized;
    }

    private String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Имя в Hermes обязательно");
        }

        String normalized = value.trim();
        return normalized.length() > MAX_DISPLAY_NAME_LENGTH
            ? normalized.substring(0, MAX_DISPLAY_NAME_LENGTH)
            : normalized;
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
        return normalized.length() > MAX_USERNAME_LENGTH
            ? normalized.substring(0, MAX_USERNAME_LENGTH)
            : normalized;
    }

    private String normalizeTenantKey(String value) {
        if (value == null || value.isBlank()) {
            return "main";
        }
        return value.trim();
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
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
