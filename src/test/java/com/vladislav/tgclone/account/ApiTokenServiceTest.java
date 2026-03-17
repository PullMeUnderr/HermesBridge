package com.vladislav.tgclone.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApiTokenServiceTest {

    @Mock
    private ApiTokenRepository apiTokenRepository;

    private ApiTokenService apiTokenService;

    @BeforeEach
    void setUp() {
        apiTokenService = new ApiTokenService(
            apiTokenRepository,
            new TokenHasher(),
            new AccountProperties("main", 365),
            Clock.fixed(Instant.parse("2026-03-17T12:00:00Z"), ZoneOffset.UTC)
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
}
