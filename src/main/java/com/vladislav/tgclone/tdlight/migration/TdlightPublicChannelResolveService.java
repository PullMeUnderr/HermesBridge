package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.tdlight.TdlightProperties;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionRepository;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.tdlight", name = "enabled", havingValue = "true")
@ConditionalOnBean(TdlightPublicChannelClient.class)
public class TdlightPublicChannelResolveService {

    private final TdlightConnectionRepository tdlightConnectionRepository;
    private final TdlightPublicChannelClient tdlightPublicChannelClient;
    private final TdlightPublicChannelReferenceParser tdlightPublicChannelReferenceParser;
    private final TdlightProperties tdlightProperties;

    public TdlightPublicChannelResolveService(
        TdlightConnectionRepository tdlightConnectionRepository,
        TdlightPublicChannelClient tdlightPublicChannelClient,
        TdlightPublicChannelReferenceParser tdlightPublicChannelReferenceParser,
        TdlightProperties tdlightProperties
    ) {
        this.tdlightConnectionRepository = tdlightConnectionRepository;
        this.tdlightPublicChannelClient = tdlightPublicChannelClient;
        this.tdlightPublicChannelReferenceParser = tdlightPublicChannelReferenceParser;
        this.tdlightProperties = tdlightProperties;
    }

    @Transactional(readOnly = true)
    public ResolvedPublicChannel resolvePublicChannel(
        UserAccount initiatedBy,
        Long tdlightConnectionId,
        String rawReference,
        String rawHandleOverride
    ) {
        if (!tdlightProperties.enabled() || !tdlightProperties.migrationEnabled()) {
            throw new IllegalStateException("TDLight public channel resolve is disabled");
        }
        if (initiatedBy == null || initiatedBy.getId() == null) {
            throw new IllegalArgumentException("initiatedBy is required");
        }

        TdlightConnection connection = tdlightConnectionRepository
            .findByIdAndUserAccount_Id(tdlightConnectionId, initiatedBy.getId())
            .filter(existing -> existing.getStatus() == TdlightConnectionStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("Active TDLight connection is required"));

        TdlightPublicChannelReferenceParser.ParsedTdlightChannelReference parsedReference =
            tdlightPublicChannelReferenceParser.parse(rawReference, rawHandleOverride);

        TdlightPublicChannelClient.TdlightResolvedChannel resolvedChannel = tdlightPublicChannelClient.resolvePublicChannel(
            connection,
            new TdlightPublicChannelClient.TdlightChannelReference(
                parsedReference.sourceChannelId(),
                parsedReference.sourceChannelHandle()
            )
        );

        return new ResolvedPublicChannel(
            parsedReference.originalReference(),
            resolvedChannel.sourceChannelId(),
            resolvedChannel.sourceChannelHandle(),
            resolvedChannel.title(),
            resolvedChannel.normalizedReference(),
            resolvedChannel.referenceKind().name(),
            resolvedChannel.publicChannel(),
            resolvedChannel.eligibility().name(),
            resolvedChannel.eligibilityReason(),
            resolvedChannel.publicChannel()
                && resolvedChannel.eligibility() == TdlightPublicChannelClient.TdlightChannelEligibility.ELIGIBLE
        );
    }

    public record ResolvedPublicChannel(
        String originalReference,
        String sourceChannelId,
        String sourceChannelHandle,
        String title,
        String normalizedReference,
        String referenceKind,
        boolean publicChannel,
        String eligibility,
        String eligibilityReason,
        boolean eligibleForMigration
    ) {
    }
}
