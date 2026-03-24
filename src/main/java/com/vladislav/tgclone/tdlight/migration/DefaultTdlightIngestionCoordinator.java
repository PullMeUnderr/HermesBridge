package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.bridge.BridgeTransport;
import com.vladislav.tgclone.bridge.TransportBindingRepository;
import com.vladislav.tgclone.conversation.Conversation;
import com.vladislav.tgclone.conversation.ConversationAttachment;
import com.vladislav.tgclone.conversation.ConversationAttachmentDraft;
import com.vladislav.tgclone.conversation.ConversationAttachmentRepository;
import com.vladislav.tgclone.conversation.ConversationInviteRepository;
import com.vladislav.tgclone.conversation.ConversationMember;
import com.vladislav.tgclone.conversation.ConversationMemberRepository;
import com.vladislav.tgclone.conversation.ConversationMessage;
import com.vladislav.tgclone.conversation.ConversationMessageRepository;
import com.vladislav.tgclone.conversation.ConversationRepository;
import com.vladislav.tgclone.conversation.ConversationService;
import com.vladislav.tgclone.tdlight.TdlightProperties;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionStatus;
import com.vladislav.tgclone.media.MediaStorageService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.tdlight", name = "enabled", havingValue = "true")
public class DefaultTdlightIngestionCoordinator implements TdlightIngestionCoordinator {

    private static final String IMPORTED_VIA = "TDLIGHT";

    private final ChannelMigrationRepository channelMigrationRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final ConversationMessageRepository conversationMessageRepository;
    private final ConversationAttachmentRepository conversationAttachmentRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationInviteRepository conversationInviteRepository;
    private final TransportBindingRepository transportBindingRepository;
    private final TdlightProperties tdlightProperties;
    private final TdlightChannelReader tdlightChannelReader;
    private final TdlightMediaImportPlanner tdlightMediaImportPlanner;
    private final MediaStorageService mediaStorageService;
    private final Clock clock;

    public DefaultTdlightIngestionCoordinator(
        ChannelMigrationRepository channelMigrationRepository,
        ConversationRepository conversationRepository,
        ConversationService conversationService,
        ConversationMessageRepository conversationMessageRepository,
        ConversationAttachmentRepository conversationAttachmentRepository,
        ConversationMemberRepository conversationMemberRepository,
        ConversationInviteRepository conversationInviteRepository,
        TransportBindingRepository transportBindingRepository,
        TdlightProperties tdlightProperties,
        TdlightChannelReader tdlightChannelReader,
        TdlightMediaImportPlanner tdlightMediaImportPlanner,
        MediaStorageService mediaStorageService,
        Clock clock
    ) {
        this.channelMigrationRepository = channelMigrationRepository;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.conversationMessageRepository = conversationMessageRepository;
        this.conversationAttachmentRepository = conversationAttachmentRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.conversationInviteRepository = conversationInviteRepository;
        this.transportBindingRepository = transportBindingRepository;
        this.tdlightProperties = tdlightProperties;
        this.tdlightChannelReader = tdlightChannelReader;
        this.tdlightMediaImportPlanner = tdlightMediaImportPlanner;
        this.mediaStorageService = mediaStorageService;
        this.clock = clock;
    }

    @Override
    @Scheduled(fixedDelayString = "15000")
    @Transactional
    public void processQueuedChannelMigrations() {
        if (!tdlightProperties.enabled() || !tdlightProperties.migrationEnabled()) {
            return;
        }

        List<ChannelMigration> migrations = channelMigrationRepository.findAllByStatusInOrderByCreatedAtAsc(
            List.of(ChannelMigrationStatus.QUEUED)
        );

        for (ChannelMigration migration : migrations) {
            processSingleMigration(migration);
        }
    }

    @Override
    @Scheduled(fixedDelayString = "1800000")
    @Transactional
    public void cleanupExpiredImportedContent() {
        Instant now = clock.instant();

        List<ConversationAttachment> expiredAttachments = conversationAttachmentRepository
            .findAllByImportedViaAndExpiresAtBefore(IMPORTED_VIA, now);
        for (ConversationAttachment attachment : expiredAttachments) {
            mediaStorageService.delete(attachment.getStorageKey());
        }
        if (!expiredAttachments.isEmpty()) {
            conversationAttachmentRepository.deleteAllByIdIn(expiredAttachments.stream().map(ConversationAttachment::getId).toList());
        }

        List<ConversationMessage> expiredMessages = conversationMessageRepository
            .findAllByImportedViaAndExpiresAtBefore(IMPORTED_VIA, now);
        List<Long> expiredMessageIds = expiredMessages.stream().map(ConversationMessage::getId).toList();
        if (!expiredMessageIds.isEmpty()) {
            conversationMessageRepository.clearReplyReferencesForMessageIds(expiredMessageIds);
            conversationMessageRepository.deleteAllByIdIn(expiredMessageIds);
        }
    }

    @Override
    @Transactional
    public ChannelMigration processMigrationNow(Long migrationId) {
        ChannelMigration migration = channelMigrationRepository.findById(migrationId)
            .orElseThrow(() -> new IllegalArgumentException("Migration not found"));
        processSingleMigration(migration);
        return migration;
    }

    @Override
    @Transactional
    public void cleanupNow() {
        purgeImportedContent();
    }

    private void processSingleMigration(ChannelMigration migration) {
        if (migration.getStatus() == ChannelMigrationStatus.COMPLETED) {
            return;
        }

        Instant now = clock.instant();
        try {
            TdlightConnection connection = migration.getTdlightConnection();
            if (connection.getStatus() != TdlightConnectionStatus.ACTIVE) {
                migration.markFailed("TDLight connection is not active", now);
                return;
            }

            migration.markRunning(now);
            UserAccount initiatedByUser = migration.getInitiatedByUser();
            Conversation conversation = resolveConversation(migration, initiatedByUser, now);
            Instant expiresAt = now.plus(tdlightProperties.importedPostRetentionDays(), ChronoUnit.DAYS);

            TdlightIngestionPolicy policy = new TdlightIngestionPolicy(
                tdlightProperties.backfillHistoryEnabled(),
                tdlightProperties.publicChannelMessageImportLimit(),
                tdlightProperties.importedPostRetentionDays(),
                tdlightProperties.mediaImportEnabled(),
                tdlightProperties.maxImportedMediaBytes(),
                tdlightProperties.maxImportedVideoDurationSeconds(),
                migration.getActivatedAt(),
                migration.getLastSeenRemoteMessageId()
            );
            TdlightChannelReader.TdlightChannelSnapshot snapshot = tdlightChannelReader.readPublicChannel(
                connection,
                migration,
                policy
            );

            int importedMessageCount = 0;
            int importedMediaCount = 0;
            String lastRemoteMessageId = migration.getLastSeenRemoteMessageId();
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
                    conversation.getId(),
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
                markImported(message, expiresAt);
                importedMessageCount += 1;
                importedMediaCount += mediaImportPlan.importedCount();
                lastRemoteMessageId = post.remoteMessageId();
            }

            migration.advanceCursor(lastRemoteMessageId, importedMessageCount, importedMediaCount, now);
            migration.markCompleted(now);
        } catch (Exception ex) {
            migration.markFailed(ex.getMessage(), now);
        }
    }

    private Conversation resolveConversation(ChannelMigration migration, UserAccount initiatedByUser, Instant now) {
        if (migration.getTargetConversationId() != null) {
            return conversationRepository.findById(migration.getTargetConversationId())
                .orElseThrow(() -> new IllegalArgumentException("Target conversation not found"));
        }

        ConversationMember membership = conversationService.createConversation(
            initiatedByUser,
            buildConversationTitle(migration)
        );
        migration.bindTargetConversation(membership.getConversation().getId(), now);
        return membership.getConversation();
    }

    private void markImported(ConversationMessage message, Instant expiresAt) {
        message.markImportedContent(IMPORTED_VIA, expiresAt);
        for (ConversationAttachment attachment : message.getAttachments()) {
            attachment.markImportedContent(IMPORTED_VIA, expiresAt);
        }
    }

    private String buildConversationTitle(ChannelMigration migration) {
        if (migration.getSourceChannelHandle() != null && !migration.getSourceChannelHandle().isBlank()) {
            return "Imported @" + migration.getSourceChannelHandle();
        }
        return "Imported " + migration.getSourceChannelId();
    }

    private void purgeImportedContent() {
        List<ChannelMigration> migrations = channelMigrationRepository.findAll();
        Set<Long> targetConversationIds = new LinkedHashSet<>();
        for (ChannelMigration migration : migrations) {
            if (migration.getTargetConversationId() != null) {
                targetConversationIds.add(migration.getTargetConversationId());
            }
        }

        List<ConversationAttachment> importedAttachments = conversationAttachmentRepository.findAllByImportedVia(IMPORTED_VIA);
        for (ConversationAttachment attachment : importedAttachments) {
            mediaStorageService.delete(attachment.getStorageKey());
        }
        if (!importedAttachments.isEmpty()) {
            conversationAttachmentRepository.deleteAllByIdIn(
                importedAttachments.stream().map(ConversationAttachment::getId).toList()
            );
        }

        List<ConversationMessage> importedMessages = conversationMessageRepository.findAllByImportedVia(IMPORTED_VIA);
        List<Long> importedMessageIds = importedMessages.stream().map(ConversationMessage::getId).toList();
        if (!importedMessageIds.isEmpty()) {
            conversationMessageRepository.clearReplyReferencesForMessageIds(importedMessageIds);
            conversationMessageRepository.deleteAllByIdIn(importedMessageIds);
        }

        if (!migrations.isEmpty()) {
            channelMigrationRepository.deleteAll(migrations);
        }

        for (Long conversationId : targetConversationIds) {
            if (conversationId == null || conversationMessageRepository.existsByConversation_Id(conversationId)) {
                continue;
            }
            transportBindingRepository.deleteAllByConversation_Id(conversationId);
            conversationInviteRepository.deleteAllByConversation_Id(conversationId);
            conversationMemberRepository.deleteAllByConversation_Id(conversationId);
            conversationRepository.findById(conversationId).ifPresent(conversationRepository::delete);
        }
    }
}
