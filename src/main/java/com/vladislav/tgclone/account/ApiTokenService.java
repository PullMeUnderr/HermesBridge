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
    private static final String ACCESS_TOKEN_LABEL = "auth-access";
    private static final String REFRESH_TOKEN_LABEL = "auth-refresh";
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
            null,
            false,
            expiresAt,
            now
        );
        apiTokenRepository.save(token);
        return new IssuedApiToken(rawToken, expiresAt, true);
    }

    @Transactional
    public Optional<AuthenticatedUser> authenticateAccessToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        return apiTokenRepository.findByTokenHashAndRevokedFalse(tokenHasher.hash(rawToken.trim()))
            .filter(token -> token.getUserAccount().isActive())
            .filter(token -> ACCESS_TOKEN_LABEL.equals(token.getLabel()))
            .filter(token -> token.getExpiresAt() == null || token.getExpiresAt().isAfter(now))
            .map(token -> {
                token.markUsed(now);
                return AuthenticatedUser.from(token.getUserAccount());
            });
    }

    @Transactional
    public Optional<AuthenticatedUser> authenticate(String rawToken) {
        return authenticateAccessToken(rawToken);
    }

    @Transactional
    public AuthSessionTokens exchangeBootstrapToken(String rawToken, boolean allowMasterToken) {
        UserAccount userAccount = resolveBootstrapUser(rawToken, allowMasterToken);
        return issueSessionForUser(userAccount);
    }

    @Transactional
    public Optional<AuthSessionTokens> refreshSession(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        return apiTokenRepository.findByTokenHashAndRevokedFalse(tokenHasher.hash(refreshToken.trim()))
            .filter(token -> REFRESH_TOKEN_LABEL.equals(token.getLabel()))
            .filter(token -> token.getSessionKey() != null && !token.getSessionKey().isBlank())
            .filter(token -> token.getUserAccount().isActive())
            .filter(token -> token.getExpiresAt() == null || token.getExpiresAt().isAfter(now))
            .map(token -> {
                revokeSessionTokens(token.getSessionKey());
                return issueSession(token.getUserAccount());
            });
    }

    @Transactional
    public void revokeSessionByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        apiTokenRepository.findByTokenHashAndRevokedFalse(tokenHasher.hash(refreshToken.trim()))
            .filter(token -> REFRESH_TOKEN_LABEL.equals(token.getLabel()))
            .map(ApiToken::getSessionKey)
            .filter(sessionKey -> !sessionKey.isBlank())
            .ifPresent(this::revokeSessionTokens);
    }

    @Transactional
    public AuthSessionTokens issueSessionForUser(UserAccount userAccount) {
        revokeExpiredSessionTokens(userAccount.getId());
        return issueSession(userAccount);
    }

    private UserAccount resolveBootstrapUser(String rawToken, boolean allowMasterToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token is required");
        }

        String trimmedToken = rawToken.trim();
        if (allowMasterToken && isMasterToken(trimmedToken)) {
            return authenticateWithMasterToken();
        }

        Instant now = clock.instant();
        return apiTokenRepository.findByTokenHashAndRevokedFalse(tokenHasher.hash(trimmedToken))
            .filter(token -> TELEGRAM_BOT_LABEL.equals(token.getLabel()))
            .filter(token -> token.getUserAccount().isActive())
            .filter(token -> token.getExpiresAt() == null || token.getExpiresAt().isAfter(now))
            .map(token -> {
                token.markUsed(now);
                return token.getUserAccount();
            })
            .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
    }

    private boolean isMasterToken(String rawToken) {
        String masterToken = normalizeNullable(accountProperties.masterToken());
        return masterToken != null
            && masterToken.equals(rawToken)
            && environment.acceptsProfiles(Profiles.of("local"));
    }

    private UserAccount authenticateWithMasterToken() {
        String username = resolveMasterUsername();
        String displayName = resolveMasterDisplayName();
        String tenantKey = resolveTenantKey();
        Instant now = clock.instant();

        return userAccountRepository.findByUsername(username)
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
    }

    private AuthSessionTokens issueSession(UserAccount userAccount) {
        Instant now = clock.instant();
        Instant accessExpiresAt = accountProperties.accessTokenTtlMinutes() <= 0
            ? null
            : now.plus(accountProperties.accessTokenTtlMinutes(), ChronoUnit.MINUTES);
        Instant refreshExpiresAt = accountProperties.refreshTokenTtlDays() <= 0
            ? null
            : now.plus(accountProperties.refreshTokenTtlDays(), ChronoUnit.DAYS);
        String sessionKey = generateSessionKey();
        String accessToken = generateRawToken();
        String refreshToken = generateRawToken();

        apiTokenRepository.save(new ApiToken(
            userAccount,
            tokenHasher.hash(accessToken),
            accessToken.substring(0, Math.min(accessToken.length(), 12)),
            null,
            ACCESS_TOKEN_LABEL,
            sessionKey,
            false,
            accessExpiresAt,
            now
        ));
        apiTokenRepository.save(new ApiToken(
            userAccount,
            tokenHasher.hash(refreshToken),
            refreshToken.substring(0, Math.min(refreshToken.length(), 12)),
            null,
            REFRESH_TOKEN_LABEL,
            sessionKey,
            false,
            refreshExpiresAt,
            now
        ));

        return new AuthSessionTokens(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt);
    }

    private void revokeExpiredSessionTokens(Long userAccountId) {
        Instant now = clock.instant();
        List<ApiToken> tokens = apiTokenRepository.findAllByUserAccount_IdAndRevokedFalse(userAccountId);
        for (ApiToken token : tokens) {
            if ((ACCESS_TOKEN_LABEL.equals(token.getLabel()) || REFRESH_TOKEN_LABEL.equals(token.getLabel()))
                && token.getExpiresAt() != null
                && !token.getExpiresAt().isAfter(now)) {
                token.revoke();
            }
        }
    }

    private void revokeSessionTokens(String sessionKey) {
        List<ApiToken> tokens = apiTokenRepository.findAllBySessionKeyAndRevokedFalse(sessionKey);
        for (ApiToken token : tokens) {
            token.revoke();
        }
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

    private String generateSessionKey() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
