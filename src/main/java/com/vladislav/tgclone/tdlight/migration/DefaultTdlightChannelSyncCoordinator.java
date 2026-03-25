package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.bridge.BridgeTransport;
import com.vladislav.tgclone.conversation.ConversationAttachment;
import com.vladislav.tgclone.conversation.ConversationAttachmentDraft;
import com.vladislav.tgclone.conversation.ConversationAttachmentRepository;
import com.vladislav.tgclone.conversation.ConversationEventPublisher;
import com.vladislav.tgclone.conversation.ConversationMessage;
import com.vladislav.tgclone.conversation.ConversationMessageRepository;
import com.vladislav.tgclone.conversation.ConversationService;
import com.vladislav.tgclone.media.MediaStorageService;
import com.vladislav.tgclone.tdlight.TdlightProperties;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionRepository;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.tdlight", name = "enabled", havingValue = "true")
public class DefaultTdlightChannelSyncCoordinator implements TdlightChannelSyncCoordinator {

    private static final String IMPORTED_VIA = "TDLIGHT";
    private static final int INITIAL_HISTORICAL_POST_COUNT = 5;
    private static final Logger log = LoggerFactory.getLogger(DefaultTdlightChannelSyncCoordinator.class);

    private final TdlightChannelSubscriptionRepository tdlightChannelSubscriptionRepository;
    private final TdlightConnectionRepository tdlightConnectionRepository;
    private final TdlightChannelReader tdlightChannelReader;
    private final TdlightMediaImportPlanner tdlightMediaImportPlanner;
    private final ConversationService conversationService;
    private final ConversationEventPublisher conversationEventPublisher;
    private final ConversationMessageRepository conversationMessageRepository;
    private final ConversationAttachmentRepository conversationAttachmentRepository;
    private final MediaStorageService mediaStorageService;
    private final TdlightProperties tdlightProperties;
    private final Clock clock;

    public DefaultTdlightChannelSyncCoordinator(
        TdlightChannelSubscriptionRepository tdlightChannelSubscriptionRepository,
        TdlightConnectionRepository tdlightConnectionRepository,
        TdlightChannelReader tdlightChannelReader,
        TdlightMediaImportPlanner tdlightMediaImportPlanner,
        ConversationService conversationService,
        ConversationEventPublisher conversationEventPublisher,
        ConversationMessageRepository conversationMessageRepository,
        ConversationAttachmentRepository conversationAttachmentRepository,
        MediaStorageService mediaStorageService,
        TdlightProperties tdlightProperties,
        Clock clock
    ) {
        this.tdlightChannelSubscriptionRepository = tdlightChannelSubscriptionRepository;
        this.tdlightConnectionRepository = tdlightConnectionRepository;
        this.tdlightChannelReader = tdlightChannelReader;
        this.tdlightMediaImportPlanner = tdlightMediaImportPlanner;
        this.conversationService = conversationService;
        this.conversationEventPublisher = conversationEventPublisher;
        this.conversationMessageRepository = conversationMessageRepository;
        this.conversationAttachmentRepository = conversationAttachmentRepository;
        this.mediaStorageService = mediaStorageService;
        this.tdlightProperties = tdlightProperties;
        this.clock = clock;
    }

    @Override
    @Scheduled(fixedDelayString = "15000")
    @Transactional
    public void syncActiveSubscriptions() {
        if (!tdlightProperties.enabled()) {
            return;
        }

        for (TdlightChannelSubscription subscription : tdlightChannelSubscriptionRepository.findAllByStatusInOrderByCreatedAtAsc(
            List.of(TdlightChannelSubscriptionStatus.ACTIVE, TdlightChannelSubscriptionStatus.FAILED)
        )) {
            syncSingleSubscription(subscription);
        }
    }

    private void syncSingleSubscription(TdlightChannelSubscription subscription) {
        Instant now = clock.instant();
        try {
            TdlightConnection activeConnection = resolveActiveConnection(subscription, now);
            TdlightIngestionPolicy policy = new TdlightIngestionPolicy(
                false,
                tdlightProperties.publicChannelMessageImportLimit(),
                subscription.getLastSyncedRemoteMessageId() == null ? INITIAL_HISTORICAL_POST_COUNT : 0,
                tdlightProperties.importedPostRetentionDays(),
                tdlightProperties.mediaImportEnabled(),
                tdlightProperties.maxImportedMediaBytes(),
                tdlightProperties.maxImportedVideoDurationSeconds(),
                subscription.getSubscribedAt(),
                subscription.getLastSyncedRemoteMessageId()
            );
            ChannelMigration syntheticMigration = new ChannelMigration(
                subscription.getUserAccount(),
                activeConnection,
                subscription.getConversationId(),
                subscription.getTelegramChannelId(),
                subscription.getTelegramChannelHandle(),
                ChannelMigrationStatus.RUNNING,
                subscription.getSubscribedAt(),
                subscription.getLastSyncedRemoteMessageId(),
                0,
                0,
                null,
                null,
                now,
                now
            );
            TdlightChannelReader.TdlightChannelSnapshot snapshot = tdlightChannelReader.readPublicChannel(
                activeConnection,
                syntheticMigration,
                policy
            );

            String lastRemoteMessageId = subscription.getLastSyncedRemoteMessageId();
            Instant expiresAt = now.plus(tdlightProperties.importedPostRetentionDays(), ChronoUnit.DAYS);
            for (TdlightChannelReader.TdlightChannelPost post : snapshot.posts()) {
                if (conversationMessageRepository.findBySourceTransportAndSourceChatIdAndSourceMessageId(
                    BridgeTransport.TELEGRAM,
                    snapshot.sourceChannelId(),
                    post.remoteMessageId()
                ).isPresent()) {
                    lastRemoteMessageId = post.remoteMessageId();
                    continue;
                }

                TdlightMediaImportPlanner.MediaImportPlan mediaImportPlan = tdlightMediaImportPlanner.plan(post, policy);
                List<ConversationAttachmentDraft> drafts = mediaImportPlan.drafts();
                ConversationMessage message = conversationService.createExternalMessage(
                    subscription.getConversationId(),
                    BridgeTransport.TELEGRAM,
                    snapshot.sourceChannelId(),
                    post.remoteMessageId(),
                    null,
                    post.authorExternalId(),
                    post.authorDisplayName(),
                    post.body(),
                    post.publishedAt(),
                    drafts
                );
                message.markImportedContent(IMPORTED_VIA, expiresAt);
                for (ConversationAttachment attachment : message.getAttachments()) {
                    attachment.markImportedContent(IMPORTED_VIA, expiresAt);
                }
                conversationEventPublisher.publish(message);
                lastRemoteMessageId = post.remoteMessageId();
            }

            if (lastRemoteMessageId != null) {
                subscription.advanceCursor(lastRemoteMessageId, now);
            } else {
                subscription.markActive(now);
            }
            activeConnection.markSynced(now);
        } catch (Exception exception) {
            String errorMessage = exception.getClass().getSimpleName()
                + (exception.getMessage() == null || exception.getMessage().isBlank() ? "" : ": " + exception.getMessage());
            log.warn(
                "TDLight sync failed subscriptionId={} conversationId={} channelId={} handle={} connectionId={} error={}",
                subscription.getId(),
                subscription.getConversationId(),
                subscription.getTelegramChannelId(),
                subscription.getTelegramChannelHandle(),
                subscription.getTdlightConnection() == null ? null : subscription.getTdlightConnection().getId(),
                errorMessage,
                exception
            );
            subscription.markFailed(errorMessage, now);
        }
    }

    private TdlightConnection resolveActiveConnection(TdlightChannelSubscription subscription, Instant now) {
        TdlightConnection currentConnection = subscription.getTdlightConnection();
        if (currentConnection != null
            && currentConnection.getStatus() == TdlightConnectionStatus.ACTIVE
            && currentConnection.getVerifiedAt() != null
            && currentConnection.getTdlightUserId() != null
            && !currentConnection.getTdlightUserId().isBlank()) {
            return currentConnection;
        }

        TdlightConnection rebound = tdlightConnectionRepository
            .findAllByUserAccount_IdOrderByCreatedAtDesc(subscription.getUserAccount().getId()).stream()
            .filter(connection -> connection.getStatus() == TdlightConnectionStatus.ACTIVE)
            .filter(connection -> connection.getVerifiedAt() != null)
            .filter(connection -> connection.getTdlightUserId() != null && !connection.getTdlightUserId().isBlank())
            .sorted(
                Comparator.comparing(TdlightConnection::getVerifiedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(TdlightConnection::getCreatedAt, Comparator.reverseOrder())
            )
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No active verified TDLight connection is available for channel sync"));

        if (currentConnection == null || !rebound.getId().equals(currentConnection.getId())) {
            subscription.rebindConnection(rebound, now);
        }
        return rebound;
    }
}
