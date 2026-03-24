package com.vladislav.tgclone.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApiTokenServiceTest {

    @Mock
    private ApiTokenRepository apiTokenRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private Environment environment;

    private ApiTokenService apiTokenService;

    @BeforeEach
    void setUp() {
        apiTokenService = new ApiTokenService(
            apiTokenRepository,
            userAccountRepository,
            new TokenHasher(),
            new AccountProperties("main", 365, 15, 30, "tgclone_refresh_token", null, null, null),
            Clock.fixed(Instant.parse("2026-03-17T12:00:00Z"), ZoneOffset.UTC),
            environment
        );
    }

    @Test
    void issueOrReuseTelegramTokenReturnsExistingPlainTextToken() {
        UserAccount userAccount = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(userAccount, "id", 7L);

        ApiToken existingToken = new ApiToken(
            userAccount,
            "hash",
            "tgc_existing",
            "tgc_existing_plain",
            "telegram-bot",
            null,
            false,
            Instant.parse("2027-03-17T12:00:00Z"),
            Instant.parse("2026-03-17T11:00:00Z")
        );

        when(apiTokenRepository.findFirstByUserAccount_IdAndLabelAndRevokedFalseOrderByCreatedAtDesc(7L, "telegram-bot"))
            .thenReturn(Optional.of(existingToken));

        IssuedApiToken issuedApiToken = apiTokenService.issueOrReuseTelegramToken(userAccount);

        assertEquals("tgc_existing_plain", issuedApiToken.plainTextToken());
        assertEquals(Instant.parse("2027-03-17T12:00:00Z"), issuedApiToken.expiresAt());
        assertFalse(issuedApiToken.createdNew());
        verify(apiTokenRepository, never()).save(existingToken);
    }

    @Test
    void issueOrReuseTelegramTokenIssuesNewTokenWhenStoredPlainTextIsMissing() {
        UserAccount userAccount = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(userAccount, "id", 7L);

        ApiToken existingToken = new ApiToken(
            userAccount,
            "hash",
            "tgc_existing",
            null,
            "telegram-bot",
            null,
            false,
            Instant.parse("2027-03-17T12:00:00Z"),
            Instant.parse("2026-03-17T11:00:00Z")
        );

        when(apiTokenRepository.findFirstByUserAccount_IdAndLabelAndRevokedFalseOrderByCreatedAtDesc(7L, "telegram-bot"))
            .thenReturn(Optional.of(existingToken));

        IssuedApiToken issuedApiToken = apiTokenService.issueOrReuseTelegramToken(userAccount);

        assertTrue(issuedApiToken.plainTextToken().startsWith("tgc_"));
        assertTrue(issuedApiToken.createdNew());
    }

    @Test
    void exchangeBootstrapTokenReturnsConfiguredMasterUserAndCreatesSessionWhenAllowed() {
        apiTokenService = new ApiTokenService(
            apiTokenRepository,
            userAccountRepository,
            new TokenHasher(),
            new AccountProperties("main", 365, 15, 30, "tgclone_refresh_token", "master-token", "dev_admin", "Dev Admin"),
            Clock.fixed(Instant.parse("2026-03-17T12:00:00Z"), ZoneOffset.UTC),
            environment
        );

        when(environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local"))).thenReturn(true);
        when(userAccountRepository.findByUsername("dev_admin")).thenReturn(Optional.empty());
        when(userAccountRepository.save(org.mockito.ArgumentMatchers.any(UserAccount.class)))
            .thenAnswer(invocation -> {
                UserAccount savedUser = invocation.getArgument(0);
                ReflectionTestUtils.setField(savedUser, "id", 42L);
                return savedUser;
            });
        List<ApiToken> savedTokens = new ArrayList<>();
        when(apiTokenRepository.save(any(ApiToken.class))).thenAnswer(invocation -> {
            ApiToken token = invocation.getArgument(0);
            savedTokens.add(token);
            return token;
        });
        when(apiTokenRepository.findAllByUserAccount_IdAndRevokedFalse(42L)).thenReturn(List.of());

        AuthSessionTokens sessionTokens = apiTokenService.exchangeBootstrapToken("master-token", true);

        assertTrue(sessionTokens.accessToken().startsWith("tgc_"));
        assertTrue(sessionTokens.refreshToken().startsWith("tgc_"));
        assertEquals(2, savedTokens.size());
        assertEquals("auth-access", savedTokens.get(0).getLabel());
        assertEquals("auth-refresh", savedTokens.get(1).getLabel());
        assertEquals(savedTokens.get(0).getSessionKey(), savedTokens.get(1).getSessionKey());

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(userCaptor.capture());
        assertEquals("main", userCaptor.getValue().getTenantKey());
        assertEquals("dev_admin", userCaptor.getValue().getUsername());
        assertEquals("Dev Admin", userCaptor.getValue().getDisplayName());
        assertTrue(userCaptor.getValue().isActive());
        assertNotNull(userCaptor.getValue().getCreatedAt());
    }

    @Test
    void exchangeBootstrapTokenRejectsMasterTokenWhenLoopbackUseIsNotAllowed() {
        apiTokenService = new ApiTokenService(
            apiTokenRepository,
            userAccountRepository,
            new TokenHasher(),
            new AccountProperties("main", 365, 15, 30, "tgclone_refresh_token", "master-token", "dev_admin", "Dev Admin"),
            Clock.fixed(Instant.parse("2026-03-17T12:00:00Z"), ZoneOffset.UTC),
            environment
        );

        assertThrows(IllegalArgumentException.class, () -> apiTokenService.exchangeBootstrapToken("master-token", false));
    }

    @Test
    void authenticateAccessTokenAcceptsOnlyAccessTokens() {
        UserAccount userAccount = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(userAccount, "id", 7L);

        ApiToken refreshToken = new ApiToken(
            userAccount,
            new TokenHasher().hash("refresh-token"),
            "refresh",
            null,
            "auth-refresh",
            "session-1",
            false,
            Instant.parse("2027-03-17T12:00:00Z"),
            Instant.parse("2026-03-17T11:00:00Z")
        );
        when(apiTokenRepository.findByTokenHashAndRevokedFalse(new TokenHasher().hash("refresh-token")))
            .thenReturn(Optional.of(refreshToken));

        assertTrue(apiTokenService.authenticateAccessToken("refresh-token").isEmpty());
    }

    @Test
    void refreshSessionRotatesRefreshAndAccessTokens() {
        UserAccount userAccount = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(userAccount, "id", 7L);

        ApiToken refreshToken = new ApiToken(
            userAccount,
            new TokenHasher().hash("refresh-token"),
            "refresh",
            null,
            "auth-refresh",
            "session-1",
            false,
            Instant.parse("2027-03-17T12:00:00Z"),
            Instant.parse("2026-03-17T11:00:00Z")
        );
        ApiToken accessToken = new ApiToken(
            userAccount,
            "access-hash",
            "access",
            null,
            "auth-access",
            "session-1",
            false,
            Instant.parse("2026-03-17T12:15:00Z"),
            Instant.parse("2026-03-17T11:00:00Z")
        );
        List<ApiToken> sessionTokens = List.of(refreshToken, accessToken);
        List<ApiToken> savedTokens = new ArrayList<>();

        when(apiTokenRepository.findByTokenHashAndRevokedFalse(new TokenHasher().hash("refresh-token")))
            .thenReturn(Optional.of(refreshToken));
        when(apiTokenRepository.findAllBySessionKeyAndRevokedFalse("session-1")).thenReturn(sessionTokens);
        when(apiTokenRepository.save(any(ApiToken.class))).thenAnswer(invocation -> {
            ApiToken token = invocation.getArgument(0);
            savedTokens.add(token);
            return token;
        });

        Optional<AuthSessionTokens> rotated = apiTokenService.refreshSession("refresh-token");

        assertTrue(rotated.isPresent());
        assertTrue(refreshToken.isRevoked());
        assertTrue(accessToken.isRevoked());
        assertEquals(2, savedTokens.size());
        assertEquals("auth-access", savedTokens.get(0).getLabel());
        assertEquals("auth-refresh", savedTokens.get(1).getLabel());
    }
}
