package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.tdlight", name = "enabled", havingValue = "true")
@ConditionalOnBean(TdlightPublicChannelGateway.class)
public class TdlightGatewayChannelReader implements TdlightChannelReader {

    private final TdlightPublicChannelGateway tdlightPublicChannelGateway;
    private final TdlightChannelSnapshotMapper tdlightChannelSnapshotMapper;

    public TdlightGatewayChannelReader(
        TdlightPublicChannelGateway tdlightPublicChannelGateway,
        TdlightChannelSnapshotMapper tdlightChannelSnapshotMapper
    ) {
        this.tdlightPublicChannelGateway = tdlightPublicChannelGateway;
        this.tdlightChannelSnapshotMapper = tdlightChannelSnapshotMapper;
    }

    @Override
    public TdlightChannelSnapshot readPublicChannel(
        TdlightConnection connection,
        ChannelMigration migration,
        TdlightIngestionPolicy policy
    ) {
        TdlightPublicChannelGateway.TdlightPublicChannelPayload payload = tdlightPublicChannelGateway.fetchPublicChannel(
            connection,
            new TdlightPublicChannelGateway.TdlightPublicChannelQuery(
                migration.getSourceChannelId(),
                migration.getSourceChannelHandle(),
                migration.getActivatedAt(),
                migration.getLastSeenRemoteMessageId(),
                policy.backfillHistoryEnabled(),
                policy.publicChannelMessageImportLimit(),
                policy.mediaImportEnabled()
            )
        );
        return tdlightChannelSnapshotMapper.map(payload, migration, policy);
    }
}
