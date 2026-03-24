package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.tdlight.TdlightProperties;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionRepository;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.tdlight", name = "enabled", havingValue = "true")
public class TdlightDiagnosticsService {

    private final TdlightConnectionRepository tdlightConnectionRepository;
    private final TdlightPublicChannelGateway tdlightPublicChannelGateway;
    private final TdlightChannelSnapshotMapper tdlightChannelSnapshotMapper;
    private final TdlightMediaImportPlanner tdlightMediaImportPlanner;
    private final TdlightPublicChannelReferenceParser tdlightPublicChannelReferenceParser;
    private final TdlightProperties tdlightProperties;
    private final Clock clock;

    public TdlightDiagnosticsService(
        TdlightConnectionRepository tdlightConnectionRepository,
        TdlightPublicChannelGateway tdlightPublicChannelGateway,
        TdlightChannelSnapshotMapper tdlightChannelSnapshotMapper,
        TdlightMediaImportPlanner tdlightMediaImportPlanner,
        TdlightPublicChannelReferenceParser tdlightPublicChannelReferenceParser,
        TdlightProperties tdlightProperties,
        Clock clock
    ) {
        this.tdlightConnectionRepository = tdlightConnectionRepository;
        this.tdlightPublicChannelGateway = tdlightPublicChannelGateway;
        this.tdlightChannelSnapshotMapper = tdlightChannelSnapshotMapper;
        this.tdlightMediaImportPlanner = tdlightMediaImportPlanner;
        this.tdlightPublicChannelReferenceParser = tdlightPublicChannelReferenceParser;
        this.tdlightProperties = tdlightProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TdlightPublicChannelDiagnostics inspectPublicChannel(
        UserAccount initiatedBy,
        Long tdlightConnectionId,
        String sourceChannelId,
        String sourceChannelHandle
    ) {
        if (!tdlightProperties.enabled() || !tdlightProperties.migrationEnabled()) {
            throw new IllegalStateException("TDLight diagnostics are disabled");
        }
        if (initiatedBy == null || initiatedBy.getId() == null) {
            throw new IllegalArgumentException("initiatedBy is required");
        }

        TdlightConnection connection = tdlightConnectionRepository
            .findByIdAndUserAccount_Id(tdlightConnectionId, initiatedBy.getId())
            .filter(existing -> existing.getStatus() == TdlightConnectionStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("Active TDLight connection is required"));

        Instant activatedAt = clock.instant();
        TdlightPublicChannelReferenceParser.ParsedTdlightChannelReference parsedReference =
            tdlightPublicChannelReferenceParser.parse(sourceChannelId, sourceChannelHandle);
        TdlightIngestionPolicy policy = new TdlightIngestionPolicy(
            tdlightProperties.backfillHistoryEnabled(),
            tdlightProperties.publicChannelMessageImportLimit(),
            tdlightProperties.importedPostRetentionDays(),
            tdlightProperties.mediaImportEnabled(),
            tdlightProperties.maxImportedMediaBytes(),
            tdlightProperties.maxImportedVideoDurationSeconds(),
            activatedAt,
            null
        );

        TdlightPublicChannelGateway.TdlightPublicChannelPayload payload = tdlightPublicChannelGateway.fetchPublicChannel(
            connection,
            new TdlightPublicChannelGateway.TdlightPublicChannelQuery(
                parsedReference.sourceChannelId(),
                parsedReference.sourceChannelHandle(),
                activatedAt,
                null,
                policy.backfillHistoryEnabled(),
                policy.publicChannelMessageImportLimit(),
                policy.mediaImportEnabled()
            )
        );

        ChannelMigration syntheticMigration = new ChannelMigration(
            initiatedBy,
            connection,
            null,
            payload.sourceChannelId(),
            payload.sourceChannelHandle(),
            ChannelMigrationStatus.QUEUED,
            activatedAt,
            null,
            0,
            0,
            null,
            activatedAt.plusSeconds(86400L * Math.max(1, policy.importedPostRetentionDays())),
            activatedAt,
            activatedAt
        );

        TdlightChannelReader.TdlightChannelSnapshot snapshot = tdlightChannelSnapshotMapper.map(payload, syntheticMigration, policy);

        List<TdlightDiagnosticsPost> posts = snapshot.posts().stream()
            .map(post -> {
                TdlightMediaImportPlanner.MediaImportPlan plan = tdlightMediaImportPlanner.plan(post, policy);
                return new TdlightDiagnosticsPost(
                    post.remoteMessageId(),
                    post.authorDisplayName(),
                    post.publishedAt(),
                    post.media() == null ? 0 : post.media().size(),
                    plan.importedCount(),
                    plan.skippedCount(),
                    plan.decisions().stream()
                        .map(decision -> new TdlightDiagnosticsMediaDecision(
                            decision.fileName(),
                            decision.mimeType(),
                            decision.sizeBytes(),
                            decision.durationSeconds(),
                            decision.imported(),
                            decision.reason()
                        ))
                        .toList()
                );
            })
            .toList();

        return new TdlightPublicChannelDiagnostics(
            connection.getId(),
            payload.sourceChannelId(),
            payload.sourceChannelHandle(),
            payload.channelTitle(),
            policy.publicChannelMessageImportLimit(),
            policy.maxImportedMediaBytes(),
            policy.maxImportedVideoDurationSeconds(),
            payload.posts() == null ? 0 : payload.posts().size(),
            snapshot.posts().size(),
            posts
        );
    }
    public record TdlightPublicChannelDiagnostics(
        Long tdlightConnectionId,
        String sourceChannelId,
        String sourceChannelHandle,
        String channelTitle,
        int messageLimit,
        long maxImportedMediaBytes,
        int maxImportedVideoDurationSeconds,
        int rawFetchedPostCount,
        int mappedPostCount,
        List<TdlightDiagnosticsPost> posts
    ) {
    }

    public record TdlightDiagnosticsPost(
        String remoteMessageId,
        String authorDisplayName,
        Instant publishedAt,
        int rawMediaCount,
        int importedMediaCount,
        int skippedMediaCount,
        List<TdlightDiagnosticsMediaDecision> mediaDecisions
    ) {
    }

    public record TdlightDiagnosticsMediaDecision(
        String fileName,
        String mimeType,
        long sizeBytes,
        int durationSeconds,
        boolean imported,
        String reason
    ) {
    }
}
