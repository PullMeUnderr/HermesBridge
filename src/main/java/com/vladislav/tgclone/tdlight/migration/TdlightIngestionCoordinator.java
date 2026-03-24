package com.vladislav.tgclone.tdlight.migration;

public interface TdlightIngestionCoordinator {

    void processQueuedChannelMigrations();

    void cleanupExpiredImportedContent();

    ChannelMigration processMigrationNow(Long migrationId);

    void cleanupNow();
}
