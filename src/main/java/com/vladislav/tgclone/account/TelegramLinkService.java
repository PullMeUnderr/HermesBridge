package com.vladislav.tgclone.account;

import com.vladislav.tgclone.security.AuthenticatedUser;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelegramLinkService {

    private static final long LINK_TOKEN_TTL_MINUTES = 10;

    private final LinkTokenRepository linkTokenRepository;
    private final TokenHasher tokenHasher;
    private final UserAccountService userAccountService;
    private final AccountIdentityService accountIdentityService;
    private final ApiTokenService apiTokenService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public TelegramLinkService(
        LinkTokenRepository linkTokenRepository,
        TokenHasher tokenHasher,
        UserAccountService userAccountService,
        AccountIdentityService accountIdentityService,
        ApiTokenService apiTokenService,
        Clock clock
    ) {
        this.linkTokenRepository = linkTokenRepository;
        this.tokenHasher = tokenHasher;
        this.userAccountService = userAccountService;
        this.accountIdentityService = accountIdentityService;
        this.apiTokenService = apiTokenService;
        this.clock = clock;
    }

    @Transactional
    public TelegramLinkToken createTelegramLinkToken(AuthenticatedUser authenticatedUser) {
        UserAccount userAccount = userAccountService.requireActiveUser(authenticatedUser.userId());
        Instant now = clock.instant();
        List<LinkToken> activeTokens = linkTokenRepository.findAllByUserAccount_IdAndProviderAndConsumedAtIsNull(
            userAccount.getId(),
            AccountIdentityProvider.TELEGRAM.value()
        );
        for (LinkToken token : activeTokens) {
            if (!token.getExpiresAt().isAfter(now)) {
                token.consume(now);
            }
        }

        String rawCode = generateRawCode();
        Instant expiresAt = now.plus(LINK_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES);
        linkTokenRepository.save(new LinkToken(
            userAccount,
            AccountIdentityProvider.TELEGRAM.value(),
            tokenHasher.hash(rawCode),
            rawCode.substring(0, Math.min(rawCode.length(), 12)),
            expiresAt,
            null,
            now
        ));
        return new TelegramLinkToken(rawCode, expiresAt);
    }

    @Transactional
    public TelegramLinkResult linkTelegramIdentity(
        String rawCode,
        String telegramUserId,
        String telegramUsername,
        String privateChatId
    ) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Нужен код привязки из Hermes");
        }

        Instant now = clock.instant();
        LinkToken linkToken = linkTokenRepository.findByTokenHash(tokenHasher.hash(rawCode.trim()))
            .filter(token -> AccountIdentityProvider.TELEGRAM.value().equals(token.getProvider()))
            .orElseThrow(() -> new IllegalArgumentException("Код привязки не найден"));

        if (linkToken.getConsumedAt() != null) {
            throw new IllegalArgumentException("Код привязки уже использован");
        }
        if (!linkToken.getExpiresAt().isAfter(now)) {
            linkToken.consume(now);
            throw new IllegalArgumentException("Код привязки истёк");
        }

        UserAccount targetAccount = userAccountService.requireActiveUser(linkToken.getUserAccount().getId());
        UserAccount existingTelegramAccount = accountIdentityService.findActiveUserByTelegramUserId(telegramUserId).orElse(null);
        if (existingTelegramAccount != null && !existingTelegramAccount.getId().equals(targetAccount.getId())) {
            throw new IllegalArgumentException("Этот Telegram уже привязан к другому Hermes account");
        }

        TelegramIdentity currentTelegramIdentity = userAccountService.findTelegramIdentityByUserId(targetAccount.getId()).orElse(null);
        if (currentTelegramIdentity != null && !currentTelegramIdentity.getTelegramUserId().equals(telegramUserId)) {
            throw new IllegalArgumentException("К этому Hermes account уже привязан другой Telegram");
        }

        boolean newlyLinked = existingTelegramAccount == null;
        accountIdentityService.ensureTelegramIdentity(targetAccount, telegramUserId, telegramUsername, privateChatId);
        linkToken.consume(now);

        IssuedApiToken compatibilityToken = apiTokenService.issueOrReuseTelegramToken(targetAccount);
        return new TelegramLinkResult(
            newlyLinked,
            targetAccount.getId(),
            targetAccount.getUsername(),
            targetAccount.getDisplayName(),
            targetAccount.getTenantKey(),
            compatibilityToken.plainTextToken(),
            compatibilityToken.expiresAt()
        );
    }

    private String generateRawCode() {
        byte[] bytes = new byte[18];
        secureRandom.nextBytes(bytes);
        return "tgc_link_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
