package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import java.util.Optional;

public interface TdlightSessionSecretStore {

    Optional<TdlightSessionEnvelope> findActiveSession(TdlightConnection connection);

    TdlightSessionEnvelope writeSession(
        TdlightConnection connection,
        String sessionKey,
        String encryptedSessionBlob,
        String encryptionKeyVersion,
        String sessionFingerprint
    );

    void revokeSession(TdlightConnection connection);
}
