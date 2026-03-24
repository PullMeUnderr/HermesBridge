package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.entitlement.EntitlementService;
import com.vladislav.tgclone.entitlement.FeatureEntitlement;
import com.vladislav.tgclone.tdlight.TdlightProperties;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionRepository;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.tdlight", name = "enabled", havingValue = "true")
public class DefaultChannelMigrationService implements ChannelMigrationService {

    private final ChannelMigrationRepository channelMigrationRepository;
    private final TdlightConnectionRepository tdlightConnectionRepository;
    private final EntitlementService entitlementService;
    private final TdlightPublicChannelReferenceParser tdlightPublicChannelReferenceParser;
    private final TdlightPublicChannelResolveService tdlightPublicChannelResolveService;
    private final TdlightProperties tdlightProperties;
    private final Clock clock;

    public DefaultChannelMigrationService(
        ChannelMigrationRepository channelMigrationRepository,
        TdlightConnectionRepository tdlightConnectionRepository,
        EntitlementService entitlementService,
        TdlightPublicChannelReferenceParser tdlightPublicChannelReferenceParser,
        TdlightPublicChannelResolveService tdlightPublicChannelResolveService,
        TdlightProperties tdlightProperties,
        Clock clock
    ) {
        this.channelMigrationRepository = channelMigrationRepository;
        this.tdlightConnectionRepository = tdlightConnectionRepository;
        this.entitlementService = entitlementService;
        this.tdlightPublicChannelReferenceParser = tdlightPublicChannelReferenceParser;
        this.tdlightPublicChannelResolveService = tdlightPublicChannelResolveService;
        this.tdlightProperties = tdlightProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ChannelMigrationSummary queuePublicChannelMigration(UserAccount initiatedBy, ChannelMigrationRequest request) {
        if (!tdlightProperties.enabled() || !tdlightProperties.migrationEnabled()) {
            throw new IllegalStateException("TDLight channel migration is disabled");
        }
        if (initiatedBy == null || initiatedBy.getId() == null) {
            throw new IllegalArgumentException("initiatedBy is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (tdlightProperties.entitlementRequired()
            && !entitlementService.hasEntitlement(initiatedBy, FeatureEntitlement.TDLIGHT_CHANNEL_MIGRATION)) {
            throw new IllegalArgumentException("TDLight channel migration is not available for this account");
        }

        TdlightConnection connection = tdlightConnectionRepository
            .findByIdAndUserAccount_Id(request.tdlightConnectionId(), initiatedBy.getId())
            .filter(existing -> existing.getStatus() == TdlightConnectionStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("Active TDLight connection is required"));

        Instant now = clock.instant();
        TdlightPublicChannelReferenceParser.ParsedTdlightChannelReference parsedReference =
            tdlightPublicChannelReferenceParser.parse(
                request.telegramChannelId(),
                request.telegramChannelHandle()
            );
        TdlightPublicChannelResolveService.ResolvedPublicChannel resolvedPublicChannel =
            tdlightPublicChannelResolveService.resolvePublicChannel(
                initiatedBy,
                request.tdlightConnectionId(),
                parsedReference.originalReference(),
                parsedReference.sourceChannelHandle()
            );
        if (!resolvedPublicChannel.eligibleForMigration()) {
            throw new IllegalArgumentException(
                "TDLight channel is not eligible for public-channel migration MVP: "
                    + resolvedPublicChannel.eligibilityReason()
            );
        }
        ChannelMigration migration = new ChannelMigration(
            initiatedBy,
            connection,
            null,
            resolvedPublicChannel.sourceChannelId(),
            resolvedPublicChannel.sourceChannelHandle(),
            ChannelMigrationStatus.QUEUED,
            now,
            null,
            0,
            0,
            null,
            now.plus(tdlightProperties.importedPostRetentionDays(), ChronoUnit.DAYS),
            now,
            now
        );

        return toSummary(channelMigrationRepository.save(migration));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChannelMigrationSummary> findLatestForChannel(UserAccount initiatedBy, String telegramChannelId) {
        if (initiatedBy == null || initiatedBy.getId() == null) {
            return Optional.empty();
        }

        return channelMigrationRepository
            .findTopByInitiatedByUser_IdAndSourceChannelIdOrderByCreatedAtDesc(
                initiatedBy.getId(),
                tdlightPublicChannelReferenceParser.parse(telegramChannelId, null).sourceChannelId()
            )
            .map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChannelMigrationSummary> findById(UserAccount initiatedBy, Long migrationId) {
        if (initiatedBy == null || initiatedBy.getId() == null || migrationId == null) {
            return Optional.empty();
        }

        return channelMigrationRepository.findByIdAndInitiatedByUser_Id(migrationId, initiatedBy.getId())
            .map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelMigrationSummary> listRecent(UserAccount initiatedBy) {
        if (initiatedBy == null || initiatedBy.getId() == null) {
            return List.of();
        }

        return channelMigrationRepository.findAllByInitiatedByUser_IdOrderByCreatedAtDesc(initiatedBy.getId()).stream()
            .map(this::toSummary)
            .toList();
    }

    private ChannelMigrationSummary toSummary(ChannelMigration migration) {
        return new ChannelMigrationSummary(
            migration.getId(),
            migration.getInitiatedByUser().getId(),
            migration.getTdlightConnection().getId(),
            migration.getTargetConversationId(),
            migration.getSourceChannelId(),
            migration.getSourceChannelHandle(),
            migration.getStatus(),
            migration.getImportedMessageCount(),
            migration.getImportedMediaCount(),
            migration.getLastError(),
            migration.getCreatedAt(),
            migration.getUpdatedAt()
        );
    }
}
