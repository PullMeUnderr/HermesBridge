package com.vladislav.tgclone.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TelegramRegistrationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private AccountIdentityService accountIdentityService;

    @Mock
    private AccountIdentityRepository accountIdentityRepository;

    @Mock
    private ApiTokenService apiTokenService;

    private TelegramRegistrationService telegramRegistrationService;

    @BeforeEach
    void setUp() {
        telegramRegistrationService = new TelegramRegistrationService(
            userAccountRepository,
            accountIdentityService,
            accountIdentityRepository,
            apiTokenService,
            new AccountProperties("main", 365, 15, 30, "tgclone_refresh_token", null, null, null),
            Clock.fixed(Instant.parse("2026-03-16T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void registerOrRefreshCreatesNewUserAndToken() {
        when(accountIdentityRepository.findByProviderAndProviderUserKey("telegram", "42")).thenReturn(Optional.empty());
        when(userAccountRepository.existsByUsername("alice")).thenReturn(false);
        when(apiTokenService.issueOrReuseTelegramToken(any(UserAccount.class))).thenReturn(
            new IssuedApiToken("plain-token", Instant.parse("2027-03-16T12:00:00Z"), true)
        );

        TelegramRegistrationResult result = telegramRegistrationService.registerOrRefresh(
            "42",
            "alice",
            "Alice",
            "100500"
        );

        assertTrue(result.created());
        assertEquals("alice", result.username());
        assertEquals("Alice", result.displayName());
        assertEquals("main", result.tenantKey());
        assertEquals("plain-token", result.plainTextToken());
        assertTrue(result.tokenCreatedNew());
        verify(userAccountRepository).save(any(UserAccount.class));
        verify(accountIdentityService).ensureTelegramIdentity(any(UserAccount.class), eq("42"), eq("alice"), eq("100500"));
    }

    @Test
    void registerOrRefreshReusesExistingIdentityAndToken() {
        UserAccount existingUser = new UserAccount("main", "telegram_42", "Old Name", true, Instant.EPOCH);
        ReflectionTestUtils.setField(existingUser, "id", 7L);

        AccountIdentity existingIdentity = new AccountIdentity(
            existingUser,
            "telegram",
            "42",
            null,
            "old_username",
            "123",
            Instant.EPOCH,
            Instant.EPOCH,
            Instant.EPOCH
        );
        when(accountIdentityRepository.findByProviderAndProviderUserKey("telegram", "42"))
            .thenReturn(Optional.of(existingIdentity));
        when(apiTokenService.issueOrReuseTelegramToken(existingUser)).thenReturn(
            new IssuedApiToken("rotated-token", Instant.parse("2027-03-16T12:00:00Z"), false)
        );

        TelegramRegistrationResult result = telegramRegistrationService.registerOrRefresh(
            "42",
            "new_username",
            "New Name",
            "777"
        );

        assertFalse(result.created());
        assertEquals(7L, result.userId());
        assertEquals("telegram_42", result.username());
        assertEquals("Old Name", result.displayName());
        assertEquals("rotated-token", result.plainTextToken());
        assertFalse(result.tokenCreatedNew());
        verify(accountIdentityService).ensureTelegramIdentity(existingUser, "42", "new_username", "777");
    }
}
