package com.vladislav.tgclone.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
            new AccountProperties("main", 365, null, null, null),
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
    void authenticateReturnsConfiguredMasterUserAndCreatesItWhenMissing() {
        apiTokenService = new ApiTokenService(
            apiTokenRepository,
            userAccountRepository,
            new TokenHasher(),
            new AccountProperties("main", 365, "master-token", "dev_admin", "Dev Admin"),
            Clock.fixed(Instant.parse("2026-03-17T12:00:00Z"), ZoneOffset.UTC),
            environment
        );

        when(environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))).thenReturn(false);
        when(userAccountRepository.findByUsername("dev_admin")).thenReturn(Optional.empty());
        when(userAccountRepository.save(org.mockito.ArgumentMatchers.any(UserAccount.class)))
            .thenAnswer(invocation -> {
                UserAccount savedUser = invocation.getArgument(0);
                ReflectionTestUtils.setField(savedUser, "id", 42L);
                return savedUser;
            });

        Optional<com.vladislav.tgclone.security.AuthenticatedUser> authenticatedUser = apiTokenService.authenticate("master-token");

        assertTrue(authenticatedUser.isPresent());
        assertEquals(42L, authenticatedUser.get().userId());
        assertEquals("dev_admin", authenticatedUser.get().username());
        assertEquals("Dev Admin", authenticatedUser.get().displayName());

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(userCaptor.capture());
        assertEquals("main", userCaptor.getValue().getTenantKey());
        assertEquals("dev_admin", userCaptor.getValue().getUsername());
        assertEquals("Dev Admin", userCaptor.getValue().getDisplayName());
        assertTrue(userCaptor.getValue().isActive());
        assertNotNull(userCaptor.getValue().getCreatedAt());
    }

    @Test
    void authenticateIgnoresMasterTokenInProdProfile() {
        apiTokenService = new ApiTokenService(
            apiTokenRepository,
            userAccountRepository,
            new TokenHasher(),
            new AccountProperties("main", 365, "master-token", "dev_admin", "Dev Admin"),
            Clock.fixed(Instant.parse("2026-03-17T12:00:00Z"), ZoneOffset.UTC),
            environment
        );

        when(environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))).thenReturn(true);

        Optional<com.vladislav.tgclone.security.AuthenticatedUser> authenticatedUser = apiTokenService.authenticate("master-token");

        assertTrue(authenticatedUser.isEmpty());
    }
}
