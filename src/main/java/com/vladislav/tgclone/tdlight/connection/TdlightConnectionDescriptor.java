package com.vladislav.tgclone.tdlight.connection;

import java.time.Instant;

public record TdlightConnectionDescriptor(
    Long id,
    Long userAccountId,
    TdlightConnectionStatus status,
    String phoneMask,
    String tdlightUserId,
    Instant createdAt,
    Instant lastVerifiedAt
) {
}
