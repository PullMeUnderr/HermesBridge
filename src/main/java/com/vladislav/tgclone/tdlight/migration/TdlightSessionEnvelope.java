package com.vladislav.tgclone.tdlight.migration;

import java.time.Instant;

public record TdlightSessionEnvelope(
    Long tdlightConnectionId,
    String sessionKey,
    String encryptedSessionBlob,
    String encryptionKeyVersion,
    String sessionFingerprint,
    Instant createdAt,
    Instant updatedAt,
    Instant revokedAt
) {

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
