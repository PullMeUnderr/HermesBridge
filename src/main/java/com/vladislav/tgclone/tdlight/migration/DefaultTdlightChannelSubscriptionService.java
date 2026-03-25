package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.conversation.Conversation;
import com.vladislav.tgclone.conversation.ConversationMember;
import com.vladislav.tgclone.conversation.ConversationMemberRepository;
import com.vladislav.tgclone.conversation.ConversationService;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionRepository;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.tdlight", name = "enabled", havingValue = "true")
public class DefaultTdlightChannelSubscriptionService implements TdlightChannelSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTdlightChannelSubscriptionService.class);
    private final TdlightConnectionRepository tdlightConnectionRepository;
    private final TdlightChannelSubscriptionRepository tdlightChannelSubscriptionRepository;
    private final TdlightPublicChannelClient tdlightPublicChannelClient;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationService conversationService;
    private final Clock clock;

    public DefaultTdlightChannelSubscriptionService(
        TdlightConnectionRepository tdlightConnectionRepository,
        TdlightChannelSubscriptionRepository tdlightChannelSubscriptionRepository,
        TdlightPublicChannelClient tdlightPublicChannelClient,
        ConversationMemberRepository conversationMemberRepository,
        ConversationService conversationService,
        Clock clock
    ) {
        this.tdlightConnectionRepository = tdlightConnectionRepository;
        this.tdlightChannelSubscriptionRepository = tdlightChannelSubscriptionRepository;
        this.tdlightPublicChannelClient = tdlightPublicChannelClient;
        this.conversationMemberRepository = conversationMemberRepository;
        this.conversationService = conversationService;
        this.clock = clock;
    }

    @Override
    public List<TdlightAvailableChannelSummary> listAvailableChannels(UserAccount userAccount, Long tdlightConnectionId) {
        TdlightConnection connection = requireActiveConnection(userAccount, tdlightConnectionId);
        Map<String, TdlightChannelSubscription> existingByChannelId = new LinkedHashMap<>();
        for (TdlightChannelSubscription subscription : tdlightChannelSubscriptionRepository
            .findAllByUserAccount_IdOrderByCreatedAtDesc(userAccount.getId())) {
            existingByChannelId.putIfAbsent(subscription.getTelegramChannelId(), subscription);
        }

        List<TdlightPublicChannelClient.TdlightAvailableChannel> availableChannels;
        try {
            availableChannels = tdlightPublicChannelClient.listAvailablePublicChannels(connection);
        } catch (IllegalStateException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage();
            if (message.toLowerCase().contains("busy")) {
                throw exception;
            }
            log.warn(
                "TDLight available channels fallback to empty list for userId={} connectionId={} reason={}",
                userAccount.getId(),
                tdlightConnectionId,
                message
            );
            return List.of();
        }

        return availableChannels.stream()
            .sorted(Comparator.comparing(channel -> channel.title() == null ? "" : channel.title(), String.CASE_INSENSITIVE_ORDER))
            .map(channel -> {
                TdlightChannelSubscription existing = existingByChannelId.get(channel.sourceChannelId());
                return new TdlightAvailableChannelSummary(
                    channel.sourceChannelId(),
                    channel.sourceChannelHandle(),
                    channel.title(),
                    channel.avatarUrl(),
                    existing != null,
                    existing == null ? null : existing.getId(),
                    existing == null ? null : existing.getConversationId()
                );
            })
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TdlightChannelSubscriptionSummary> listSubscriptions(UserAccount userAccount) {
        return tdlightChannelSubscriptionRepository.findAllByUserAccount_IdOrderByCreatedAtDesc(userAccount.getId()).stream()
            .map(TdlightChannelSubscriptionSummary::from)
            .toList();
    }

    @Override
    public TdlightChannelSubscriptionSummary subscribe(UserAccount userAccount, TdlightChannelSubscriptionRequest request) {
        if (request == null || request.tdlightConnectionId() == null) {
            throw new IllegalArgumentException("tdlightConnectionId is required");
        }
        if (request.telegramChannelId() == null || request.telegramChannelId().isBlank()) {
            throw new IllegalArgumentException("telegramChannelId is required");
        }

        TdlightConnection connection = requireActiveConnection(userAccount, request.tdlightConnectionId());
        TdlightChannelSubscription existing = tdlightChannelSubscriptionRepository
            .findByUserAccount_IdAndTelegramChannelId(userAccount.getId(), request.telegramChannelId().trim())
            .orElse(null);
        if (existing != null) {
            existing.markActive(clock.instant());
            applyConversationAvatar(existing.getConversationId(), request.avatarUrl());
            return TdlightChannelSubscriptionSummary.from(existing);
        }

        Instant now = clock.instant();
        ConversationMember membership = conversationService.createConversation(
            userAccount,
            "Telegram: " + normalizeTitle(request.channelTitle(), request.telegramChannelHandle(), request.telegramChannelId())
        );
        TdlightChannelSubscription subscription = new TdlightChannelSubscription(
            userAccount,
            connection,
            membership.getConversation().getId(),
            request.telegramChannelId().trim(),
            trimToNull(request.telegramChannelHandle()),
            normalizeTitle(request.channelTitle(), request.telegramChannelHandle(), request.telegramChannelId()),
            TdlightChannelSubscriptionStatus.ACTIVE,
            now,
            null,
            null,
            null,
            now,
            now
        );
        TdlightChannelSubscription savedSubscription = tdlightChannelSubscriptionRepository.save(subscription);
        applyConversationAvatar(membership.getConversation().getId(), request.avatarUrl());
        return TdlightChannelSubscriptionSummary.from(savedSubscription);
    }

    @Override
    @Transactional
    public void disconnectByConversation(UserAccount userAccount, Long conversationId) {
        if (userAccount == null || userAccount.getId() == null) {
            throw new IllegalArgumentException("userAccount is required");
        }
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId is required");
        }

        TdlightChannelSubscription subscription = tdlightChannelSubscriptionRepository
            .findByUserAccount_IdAndConversationId(userAccount.getId(), conversationId)
            .orElse(null);

        if (subscription == null) {
            ConversationMember membership = conversationMemberRepository
                .findByConversation_IdAndUserAccount_Id(conversationId, userAccount.getId())
                .orElseThrow(() -> new IllegalArgumentException("TDLight channel subscription not found"));
            Conversation conversation = membership.getConversation();
            boolean orphanTelegramChannel = conversation.getTenantKey().equals(userAccount.getTenantKey())
                && conversation.getTitle() != null
                && conversation.getTitle().startsWith("Telegram: ");
            if (!orphanTelegramChannel) {
                throw new IllegalArgumentException("TDLight channel subscription not found");
            }
            log.warn(
                "Deleting orphan TDLight conversation without subscription userId={} conversationId={} title={}",
                userAccount.getId(),
                conversationId,
                conversation.getTitle()
            );
            conversationService.deleteConversationSystem(conversationId);
            return;
        }

        tdlightChannelSubscriptionRepository.delete(subscription);
        tdlightChannelSubscriptionRepository.flush();
        conversationService.deleteConversationSystem(conversationId);
    }

    private TdlightConnection requireActiveConnection(UserAccount userAccount, Long connectionId) {
        return tdlightConnectionRepository.findByIdAndUserAccount_Id(connectionId, userAccount.getId())
            .filter(connection -> connection.getStatus() == TdlightConnectionStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("Active TDLight connection is required"));
    }

    private String normalizeTitle(String title, String handle, String channelId) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (handle != null && !handle.isBlank()) {
            return "@" + handle.trim();
        }
        return channelId.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void applyConversationAvatar(Long conversationId, String avatarUrl) {
        if (conversationId == null || avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }

        try {
            ConversationService.AvatarUpload avatarUpload = decodeAvatarDataUrl(avatarUrl);
            if (avatarUpload == null || avatarUpload.content() == null || avatarUpload.content().length == 0) {
                return;
            }
            conversationService.updateConversationAvatarSystem(conversationId, avatarUpload);
        } catch (Exception exception) {
            log.warn(
                "Failed to apply TDLight channel avatar conversationId={} reason={}",
                conversationId,
                exception.getMessage()
            );
        }
    }

    private ConversationService.AvatarUpload decodeAvatarDataUrl(String avatarUrl) {
        String normalized = trimToNull(avatarUrl);
        if (normalized == null || !normalized.startsWith("data:")) {
            return null;
        }

        int commaIndex = normalized.indexOf(',');
        if (commaIndex <= 5) {
            return null;
        }

        String metadata = normalized.substring(5, commaIndex);
        String payload = normalized.substring(commaIndex + 1);
        if (!metadata.contains(";base64") || payload.isBlank()) {
            return null;
        }

        String mimeType = metadata.replace(";base64", "").trim();
        if (mimeType.isBlank()) {
            mimeType = "image/jpeg";
        }

        return new ConversationService.AvatarUpload(
            "channel-avatar." + extensionForMimeType(mimeType),
            mimeType,
            Base64.getDecoder().decode(payload)
        );
    }

    private String extensionForMimeType(String mimeType) {
        String normalized = mimeType == null ? "" : mimeType.trim().toLowerCase();
        return switch (normalized) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }
}
