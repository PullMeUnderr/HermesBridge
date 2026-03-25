package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnProperty(prefix = "app.tdlight", name = "public-channel-client-mode", havingValue = "REAL")
public class JpaTdlightSessionStateStore implements TdlightSessionStateStore {

    private final Clock clock;
    private final TdlightSessionBindingRepository tdlightSessionBindingRepository;

    public JpaTdlightSessionStateStore(
        Clock clock,
        TdlightSessionBindingRepository tdlightSessionBindingRepository
    ) {
        this.clock = clock;
        this.tdlightSessionBindingRepository = tdlightSessionBindingRepository;
    }

    @Override
    public Optional<TdlightSessionBinding> findBinding(TdlightConnection connection) {
        if (connection == null || connection.getId() == null) {
            return Optional.empty();
        }
        return tdlightSessionBindingRepository.findByTdlightConnectionIdAndRevokedAtIsNull(connection.getId())
            .map(this::toBinding);
    }

    @Override
    public TdlightSessionBinding createBinding(
        TdlightConnection connection,
        String databaseDirectory,
        String filesDirectory
    ) {
        Instant now = clock.instant();
        Long connectionId = requireConnectionId(connection);
        String sessionKey = "tdlight-session-" + connectionId;
        TdlightSessionBindingEntity entity = tdlightSessionBindingRepository.save(
            new TdlightSessionBindingEntity(
                connection,
                sessionKey,
                resolveSessionDirectory(databaseDirectory, sessionKey),
                resolveSessionDirectory(filesDirectory, sessionKey),
                now,
                now,
                now,
                null
            )
        );
        return toBinding(entity);
    }

    @Override
    public void revokeBinding(TdlightConnection connection) {
        if (connection == null || connection.getId() == null) {
            return;
        }
        tdlightSessionBindingRepository.findByTdlightConnectionIdAndRevokedAtIsNull(connection.getId())
            .ifPresent(entity -> {
                entity.markRevoked(clock.instant());
                tdlightSessionBindingRepository.save(entity);
            });
    }

    private TdlightSessionBinding toBinding(TdlightSessionBindingEntity entity) {
        return new TdlightSessionBinding(
            entity.getTdlightConnection().getId(),
            entity.getSessionKey(),
            entity.getDatabaseDirectory(),
            entity.getFilesDirectory(),
            entity.getCreatedAt()
        );
    }

    private Long requireConnectionId(TdlightConnection connection) {
        if (connection == null || connection.getId() == null) {
            throw new IllegalArgumentException("TDLight connection id is required for session binding");
        }
        return connection.getId();
    }

    private String resolveSessionDirectory(String baseDirectory, String sessionKey) {
        if (baseDirectory == null || baseDirectory.isBlank()) {
            throw new IllegalArgumentException("TDLight session base directory is required");
        }
        return Path.of(baseDirectory.trim()).resolve(sessionKey).toString();
    }
}
