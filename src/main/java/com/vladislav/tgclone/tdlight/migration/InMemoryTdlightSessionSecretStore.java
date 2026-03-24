package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import com.vladislav.tgclone.tdlight.condition.ConditionalOnTdlightRealMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnTdlightRealMode
@ConditionalOnMissingBean(TdlightSessionSecretStore.class)
public class InMemoryTdlightSessionSecretStore implements TdlightSessionSecretStore {

    private final Clock clock;
    private final Map<Long, TdlightSessionEnvelope> envelopes = new ConcurrentHashMap<>();

    public InMemoryTdlightSessionSecretStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<TdlightSessionEnvelope> findActiveSession(TdlightConnection connection) {
        if (connection == null || connection.getId() == null) {
            return Optional.empty();
        }
        TdlightSessionEnvelope envelope = envelopes.get(connection.getId());
        if (envelope == null || envelope.isRevoked()) {
            return Optional.empty();
        }
        return Optional.of(envelope);
    }

    @Override
    public TdlightSessionEnvelope writeSession(
        TdlightConnection connection,
        String sessionKey,
        String encryptedSessionBlob,
        String encryptionKeyVersion,
        String sessionFingerprint
    ) {
        Long connectionId = requireConnectionId(connection);
        Instant now = clock.instant();
        TdlightSessionEnvelope envelope = new TdlightSessionEnvelope(
            connectionId,
            sessionKey,
            encryptedSessionBlob,
            encryptionKeyVersion,
            sessionFingerprint,
            now,
            now,
            null
        );
        envelopes.put(connectionId, envelope);
        return envelope;
    }

    @Override
    public void revokeSession(TdlightConnection connection) {
        if (connection == null || connection.getId() == null) {
            return;
        }
        TdlightSessionEnvelope current = envelopes.get(connection.getId());
        if (current == null) {
            return;
        }
        envelopes.put(
            connection.getId(),
            new TdlightSessionEnvelope(
                current.tdlightConnectionId(),
                current.sessionKey(),
                current.encryptedSessionBlob(),
                current.encryptionKeyVersion(),
                current.sessionFingerprint(),
                current.createdAt(),
                clock.instant(),
                clock.instant()
            )
        );
    }

    private Long requireConnectionId(TdlightConnection connection) {
        if (connection == null || connection.getId() == null) {
            throw new IllegalArgumentException("TDLight connection id is required for session secrets");
        }
        return connection.getId();
    }
}
