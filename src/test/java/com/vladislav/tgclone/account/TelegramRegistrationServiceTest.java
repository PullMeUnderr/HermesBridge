package com.vladislav.tgclone.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private TelegramIdentityRepository telegramIdentityRepository;

    @Mock
    private ApiTokenService apiTokenService;

    private TelegramRegistrationService telegramRegistrationService;

    @BeforeEach
    void setUp() {
        telegramRegistrationService = new TelegramRegistrationService(
            userAccountRepository,
            telegramIdentityRepository,
            apiTokenService,
            new AccountProperties("main", 365),
            Clock.fixed(Instant.parse("2026-03-16T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void registerOrRefreshCreatesNewUserAndToken() {
        when(telegramIdentityRepository.findByTelegramUserId("42")).thenReturn(Optional.empty());
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
        verify(telegramIdentityRepository).save(any(TelegramIdentity.class));
    }

    @Test
    void registerOrRefreshUpdatesExistingIdentityAndReusesToken() {
        UserAccount existingUser = new UserAccount("main", "telegram_42", "Old Name", true, Instant.EPOCH);
        ReflectionTestUtils.setField(existingUser, "id", 7L);

        TelegramIdentity existingIdentity = new TelegramIdentity(
            existingUser,
            "42",
            "old_username",
            "123",
            Instant.EPOCH,
            Instant.EPOCH
        );
        when(telegramIdentityRepository.findByTelegramUserId("42")).thenReturn(Optional.of(existingIdentity));
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
        assertEquals("New Name", result.displayName());
        assertEquals("rotated-token", result.plainTextToken());
        assertFalse(result.tokenCreatedNew());
        assertNotNull(existingIdentity.getLastSeenAt());
        assertEquals("777", existingIdentity.getPrivateChatId());
        assertEquals("new_username", existingIdentity.getTelegramUsername());
    }
}
