package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnProperty(prefix = "app.tdlight", name = "public-channel-client-mode", havingValue = "REAL")
public class JpaTdlightSessionSecretStore implements TdlightSessionSecretStore {

    private final Clock clock;
    private final TdlightSessionBindingRepository tdlightSessionBindingRepository;
    private final TdlightSessionSecretRepository tdlightSessionSecretRepository;

    public JpaTdlightSessionSecretStore(
        Clock clock,
        TdlightSessionBindingRepository tdlightSessionBindingRepository,
        TdlightSessionSecretRepository tdlightSessionSecretRepository
    ) {
        this.clock = clock;
        this.tdlightSessionBindingRepository = tdlightSessionBindingRepository;
        this.tdlightSessionSecretRepository = tdlightSessionSecretRepository;
    }

    @Override
    public Optional<TdlightSessionEnvelope> findActiveSession(TdlightConnection connection) {
        if (connection == null || connection.getId() == null) {
            return Optional.empty();
        }
        return tdlightSessionSecretRepository
            .findFirstByTdlightConnectionIdAndRevokedAtIsNullOrderByUpdatedAtDesc(connection.getId())
            .map(this::toEnvelope);
    }

    @Override
    public TdlightSessionEnvelope writeSession(
        TdlightConnection connection,
        String sessionKey,
        String encryptedSessionBlob,
        String encryptionKeyVersion,
        String sessionFingerprint
    ) {
        TdlightSessionBindingEntity binding = tdlightSessionBindingRepository
            .findByTdlightConnectionIdAndRevokedAtIsNull(requireConnectionId(connection))
            .orElseThrow(() -> new IllegalStateException("Active TDLight session binding is required before writing secrets"));

        Instant now = clock.instant();
        TdlightSessionSecretEntity entity = tdlightSessionSecretRepository.save(
            new TdlightSessionSecretEntity(
                connection,
                binding,
                sessionKey,
                encryptedSessionBlob,
                encryptionKeyVersion,
                sessionFingerprint,
                now,
                now,
                null
            )
        );
        return toEnvelope(entity);
    }

    @Override
    public void revokeSession(TdlightConnection connection) {
        if (connection == null || connection.getId() == null) {
            return;
        }
        tdlightSessionSecretRepository.findFirstByTdlightConnectionIdAndRevokedAtIsNullOrderByUpdatedAtDesc(connection.getId())
            .ifPresent(entity -> {
                entity.markRevoked(clock.instant());
                tdlightSessionSecretRepository.save(entity);
            });
    }

    private TdlightSessionEnvelope toEnvelope(TdlightSessionSecretEntity entity) {
        return new TdlightSessionEnvelope(
            entity.getTdlightConnection().getId(),
            entity.getSessionKey(),
            entity.getEncryptedSessionBlob(),
            entity.getEncryptionKeyVersion(),
            entity.getSessionFingerprint(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getRevokedAt()
        );
    }

    private Long requireConnectionId(TdlightConnection connection) {
        if (connection == null || connection.getId() == null) {
            throw new IllegalArgumentException("TDLight connection id is required for session secrets");
        }
        return connection.getId();
    }
}
