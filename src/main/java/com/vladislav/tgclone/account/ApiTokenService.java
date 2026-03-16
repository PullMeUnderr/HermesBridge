package com.vladislav.tgclone.account;

import com.vladislav.tgclone.security.AuthenticatedUser;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiTokenService {

    private static final String TELEGRAM_BOT_LABEL = "telegram-bot";

    private final ApiTokenRepository apiTokenRepository;
    private final TokenHasher tokenHasher;
    private final AccountProperties accountProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiTokenService(
        ApiTokenRepository apiTokenRepository,
        TokenHasher tokenHasher,
        AccountProperties accountProperties,
        Clock clock
    ) {
        this.apiTokenRepository = apiTokenRepository;
        this.tokenHasher = tokenHasher;
        this.accountProperties = accountProperties;
        this.clock = clock;
    }

    @Transactional
    public IssuedApiToken rotateTelegramToken(UserAccount userAccount) {
        revokeActiveTokens(userAccount.getId());

        Instant now = clock.instant();
        Instant expiresAt = accountProperties.apiTokenTtlDays() <= 0
            ? null
            : now.plus(accountProperties.apiTokenTtlDays(), ChronoUnit.DAYS);

        String rawToken = generateRawToken();
        ApiToken token = new ApiToken(
            userAccount,
            tokenHasher.hash(rawToken),
            rawToken.substring(0, Math.min(rawToken.length(), 12)),
            TELEGRAM_BOT_LABEL,
            false,
            expiresAt,
            now
        );
        apiTokenRepository.save(token);
        return new IssuedApiToken(rawToken, expiresAt);
    }

    @Transactional
    public Optional<AuthenticatedUser> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        return apiTokenRepository.findByTokenHashAndRevokedFalse(tokenHasher.hash(rawToken.trim()))
            .filter(token -> token.getUserAccount().isActive())
            .filter(token -> token.getExpiresAt() == null || token.getExpiresAt().isAfter(now))
            .map(token -> {
                token.markUsed(now);
                return AuthenticatedUser.from(token.getUserAccount());
            });
    }

    private void revokeActiveTokens(Long userAccountId) {
        List<ApiToken> tokens = apiTokenRepository.findAllByUserAccount_IdAndRevokedFalse(userAccountId);
        for (ApiToken token : tokens) {
            token.revoke();
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "tgc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
