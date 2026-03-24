package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.account.UserAccount;
import java.util.List;
import java.util.Optional;

public interface ChannelMigrationService {

    ChannelMigrationSummary queuePublicChannelMigration(UserAccount initiatedBy, ChannelMigrationRequest request);

    Optional<ChannelMigrationSummary> findLatestForChannel(UserAccount initiatedBy, String telegramChannelId);

    Optional<ChannelMigrationSummary> findById(UserAccount initiatedBy, Long migrationId);

    List<ChannelMigrationSummary> listRecent(UserAccount initiatedBy);
}
