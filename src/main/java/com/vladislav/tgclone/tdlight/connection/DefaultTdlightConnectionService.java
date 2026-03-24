package com.vladislav.tgclone.tdlight.connection;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.tdlight.TdlightProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.tdlight", name = "enabled", havingValue = "true")
public class DefaultTdlightConnectionService implements TdlightConnectionService {

    private final TdlightConnectionRepository tdlightConnectionRepository;
    private final TdlightProperties tdlightProperties;
    private final Clock clock;

    public DefaultTdlightConnectionService(
        TdlightConnectionRepository tdlightConnectionRepository,
        TdlightProperties tdlightProperties,
        Clock clock
    ) {
        this.tdlightConnectionRepository = tdlightConnectionRepository;
        this.tdlightProperties = tdlightProperties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TdlightConnectionDescriptor> findPrimaryConnection(UserAccount userAccount) {
        if (userAccount == null || userAccount.getId() == null) {
            return Optional.empty();
        }

        return tdlightConnectionRepository
            .findFirstByUserAccount_IdAndStatusOrderByCreatedAtDesc(userAccount.getId(), TdlightConnectionStatus.ACTIVE)
            .map(this::toDescriptor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TdlightConnectionDescriptor> listConnections(UserAccount userAccount) {
        if (userAccount == null || userAccount.getId() == null) {
            return List.of();
        }

        return tdlightConnectionRepository.findAllByUserAccount_IdOrderByCreatedAtDesc(userAccount.getId()).stream()
            .map(this::toDescriptor)
            .toList();
    }

    @Override
    @Transactional
    public TdlightConnectionDescriptor createDevelopmentConnection(
        UserAccount userAccount,
        String phoneMask,
        String tdlightUserId
    ) {
        if (!tdlightProperties.enabled()) {
            throw new IllegalStateException("TDLight is disabled");
        }
        if (userAccount == null || userAccount.getId() == null) {
            throw new IllegalArgumentException("userAccount is required");
        }

        Optional<TdlightConnectionDescriptor> existing = findPrimaryConnection(userAccount);
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant now = clock.instant();
        TdlightConnection connection = new TdlightConnection(
            userAccount,
            TdlightConnectionStatus.ACTIVE,
            normalizeNullable(phoneMask),
            normalizeNullable(tdlightUserId),
            null,
            null,
            "dev-placeholder-session",
            "dev",
            now,
            now,
            null,
            null
        );
        return toDescriptor(tdlightConnectionRepository.save(connection));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveConnection(UserAccount userAccount) {
        return findPrimaryConnection(userAccount).isPresent();
    }

    private TdlightConnectionDescriptor toDescriptor(TdlightConnection connection) {
        return new TdlightConnectionDescriptor(
            connection.getId(),
            connection.getUserAccount().getId(),
            connection.getStatus(),
            connection.getPhoneMask(),
            connection.getTdlightUserId(),
            connection.getCreatedAt(),
            connection.getVerifiedAt()
        );
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
