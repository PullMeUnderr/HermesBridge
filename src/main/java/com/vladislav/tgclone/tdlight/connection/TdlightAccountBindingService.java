package com.vladislav.tgclone.tdlight.connection;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.tdlight.migration.TdlightRuntimeAdapter;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.tdlight", name = "enabled", havingValue = "true")
public class TdlightAccountBindingService {

    private final TdlightConnectionRepository tdlightConnectionRepository;
    private final Clock clock;

    public TdlightAccountBindingService(
        TdlightConnectionRepository tdlightConnectionRepository,
        Clock clock
    ) {
        this.tdlightConnectionRepository = tdlightConnectionRepository;
        this.clock = clock;
    }

    @Transactional
    public TdlightBoundAccount syncAuthorizedAccount(
        TdlightConnection connection,
        TdlightRuntimeAdapter.TdlightAuthorizedUser authorizedUser
    ) {
        if (connection == null || connection.getId() == null || connection.getUserAccount() == null) {
            throw new IllegalArgumentException("connection is required");
        }
        if (authorizedUser == null) {
            throw new IllegalArgumentException("authorizedUser is required");
        }

        String telegramUserId = normalizeRequired(authorizedUser.telegramUserId(), "telegramUserId is required");
        tdlightConnectionRepository.findFirstByTdlightUserIdAndUserAccount_IdNot(
            telegramUserId,
            connection.getUserAccount().getId()
        ).ifPresent(existing -> {
            throw new IllegalArgumentException("Этот Telegram уже привязан к другому Hermes account");
        });

        Instant now = clock.instant();
        connection.updateAuthorizedProfile(
            telegramUserId,
            normalizeNullable(authorizedUser.telegramUsername()),
            normalizeNullable(authorizedUser.displayName()),
            now
        );
        TdlightConnection saved = tdlightConnectionRepository.save(connection);
        return toBoundAccount(saved);
    }

    @Transactional(readOnly = true)
    public Optional<TdlightBoundAccount> findBoundAccount(UserAccount userAccount) {
        if (userAccount == null || userAccount.getId() == null) {
            return Optional.empty();
        }

        return tdlightConnectionRepository
            .findFirstByUserAccount_IdAndStatusOrderByCreatedAtDesc(userAccount.getId(), TdlightConnectionStatus.ACTIVE)
            .filter(connection -> connection.getVerifiedAt() != null)
            .filter(connection -> connection.getTdlightUserId() != null && !connection.getTdlightUserId().isBlank())
            .map(this::toBoundAccount);
    }

    private TdlightBoundAccount toBoundAccount(TdlightConnection connection) {
        return new TdlightBoundAccount(
            connection.getId(),
            connection.getTdlightUserId(),
            connection.getTdlightUsername(),
            connection.getTdlightDisplayName(),
            connection.getVerifiedAt()
        );
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

    public record TdlightBoundAccount(
        Long tdlightConnectionId,
        String telegramUserId,
        String telegramUsername,
        String displayName,
        Instant verifiedAt
    ) {
    }
}
