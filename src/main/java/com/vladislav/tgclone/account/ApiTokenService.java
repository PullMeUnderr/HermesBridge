package com.vladislav.tgclone.account;

import com.vladislav.tgclone.security.AuthenticatedUser;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiTokenService {

    private static final String TELEGRAM_BOT_LABEL = "telegram-bot";
    private static final String DEFAULT_MASTER_USERNAME = "local_admin";
    private static final String DEFAULT_MASTER_DISPLAY_NAME = "Local Admin";

    private final ApiTokenRepository apiTokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final TokenHasher tokenHasher;
    private final AccountProperties accountProperties;
    private final Clock clock;
    private final Environment environment;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiTokenService(
        ApiTokenRepository apiTokenRepository,
        UserAccountRepository userAccountRepository,
        TokenHasher tokenHasher,
        AccountProperties accountProperties,
        Clock clock,
        Environment environment
    ) {
        this.apiTokenRepository = apiTokenRepository;
        this.userAccountRepository = userAccountRepository;
        this.tokenHasher = tokenHasher;
        this.accountProperties = accountProperties;
        this.clock = clock;
        this.environment = environment;
    }

    @Transactional
    public IssuedApiToken issueOrReuseTelegramToken(UserAccount userAccount) {
        Instant now = clock.instant();
        Optional<ApiToken> existingToken = apiTokenRepository
            .findFirstByUserAccount_IdAndLabelAndRevokedFalseOrderByCreatedAtDesc(userAccount.getId(), TELEGRAM_BOT_LABEL)
            .filter(token -> token.getExpiresAt() == null || token.getExpiresAt().isAfter(now))
            .filter(token -> token.getPlainTextToken() != null && !token.getPlainTextToken().isBlank());

        if (existingToken.isPresent()) {
            ApiToken token = existingToken.get();
            return new IssuedApiToken(token.getPlainTextToken(), token.getExpiresAt(), false);
        }

        revokeActiveTelegramTokens(userAccount.getId());
        Instant expiresAt = accountProperties.apiTokenTtlDays() <= 0
            ? null
            : now.plus(accountProperties.apiTokenTtlDays(), ChronoUnit.DAYS);

        String rawToken = generateRawToken();
        ApiToken token = new ApiToken(
            userAccount,
            tokenHasher.hash(rawToken),
            rawToken.substring(0, Math.min(rawToken.length(), 12)),
            rawToken,
            TELEGRAM_BOT_LABEL,
            false,
            expiresAt,
            now
        );
        apiTokenRepository.save(token);
        return new IssuedApiToken(rawToken, expiresAt, true);
    }

    @Transactional
    public Optional<AuthenticatedUser> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        String trimmedToken = rawToken.trim();
        if (isMasterToken(trimmedToken)) {
            return Optional.of(authenticateWithMasterToken());
        }

        Instant now = clock.instant();
        return apiTokenRepository.findByTokenHashAndRevokedFalse(tokenHasher.hash(trimmedToken))
            .filter(token -> token.getUserAccount().isActive())
            .filter(token -> token.getExpiresAt() == null || token.getExpiresAt().isAfter(now))
            .map(token -> {
                token.markUsed(now);
                return AuthenticatedUser.from(token.getUserAccount());
            });
    }

    private boolean isMasterToken(String rawToken) {
        String masterToken = normalizeNullable(accountProperties.masterToken());
        return masterToken != null
            && masterToken.equals(rawToken)
            && !environment.acceptsProfiles(Profiles.of("prod"));
    }

    private AuthenticatedUser authenticateWithMasterToken() {
        String username = resolveMasterUsername();
        String displayName = resolveMasterDisplayName();
        String tenantKey = resolveTenantKey();
        Instant now = clock.instant();

        UserAccount userAccount = userAccountRepository.findByUsername(username)
            .map(existingUser -> {
                existingUser.activate();
                existingUser.updateDisplayName(displayName);
                return existingUser;
            })
            .orElseGet(() -> userAccountRepository.save(
                new UserAccount(
                    tenantKey,
                    username,
                    displayName,
                    true,
                    now
                )
            ));

        return AuthenticatedUser.from(userAccount);
    }

    private void revokeActiveTelegramTokens(Long userAccountId) {
        List<ApiToken> tokens = apiTokenRepository.findAllByUserAccount_IdAndRevokedFalse(userAccountId);
        for (ApiToken token : tokens) {
            if (TELEGRAM_BOT_LABEL.equals(token.getLabel())) {
                token.revoke();
            }
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "tgc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String resolveMasterUsername() {
        String configuredValue = normalizeNullable(accountProperties.masterTokenUsername());
        if (configuredValue == null) {
            return DEFAULT_MASTER_USERNAME;
        }

        String normalized = configuredValue.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+", "")
            .replaceAll("_+$", "");

        if (normalized.isBlank()) {
            return DEFAULT_MASTER_USERNAME;
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = "user_" + normalized;
        }
        return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
    }

    private String resolveMasterDisplayName() {
        String configuredValue = normalizeNullable(accountProperties.masterTokenDisplayName());
        if (configuredValue == null) {
            return DEFAULT_MASTER_DISPLAY_NAME;
        }
        return configuredValue.length() > 255 ? configuredValue.substring(0, 255) : configuredValue;
    }

    private String resolveTenantKey() {
        String configuredValue = normalizeNullable(accountProperties.defaultTenantKey());
        return configuredValue == null ? "main" : configuredValue;
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
