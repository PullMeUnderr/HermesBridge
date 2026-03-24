package com.vladislav.tgclone.tdlight.migration;

import java.time.Instant;

public record TdlightSessionBinding(
    Long tdlightConnectionId,
    String sessionKey,
    String databaseDirectory,
    String filesDirectory,
    Instant createdAt
) {
}
