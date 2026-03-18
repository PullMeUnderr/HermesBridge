package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.account.TokenHasher;
import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.common.ForbiddenException;
import com.vladislav.tgclone.bridge.BridgeTransport;
import com.vladislav.tgclone.bridge.DeliveryRecordRepository;
import com.vladislav.tgclone.bridge.TransportBindingRepository;
import com.vladislav.tgclone.common.NotFoundException;
import com.vladislav.tgclone.media.MediaStorageService;
import com.vladislav.tgclone.media.StoredMediaFile;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.vladislav.tgclone.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private static final int MAX_CONVERSATION_TITLE_LENGTH = 200;
    private static final int MAX_CONVERSATION_AVATAR_FILENAME_LENGTH = 255;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationInviteRepository conversationInviteRepository;
    private final ConversationAttachmentRepository conversationAttachmentRepository;
    private final TransportBindingRepository transportBindingRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final ConversationAttachmentService conversationAttachmentService;
    private final UserAccountService userAccountService;
    private final MediaStorageService mediaStorageService;
    private final ConversationProperties conversationProperties;
    private final TokenHasher tokenHasher;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public ConversationService(
        ConversationRepository conversationRepository,
        ConversationMessageRepository conversationMessageRepository,
        ConversationMemberRepository conversationMemberRepository,
        ConversationInviteRepository conversationInviteRepository,
        ConversationAttachmentRepository conversationAttachmentRepository,
        TransportBindingRepository transportBindingRepository,
        DeliveryRecordRepository deliveryRecordRepository,
        ConversationAttachmentService conversationAttachmentService,
        UserAccountService userAccountService,
        MediaStorageService mediaStorageService,
        ConversationProperties conversationProperties,
        TokenHasher tokenHasher,
        Clock clock
    ) {
        this.conversationRepository = conversationRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.conversationInviteRepository = conversationInviteRepository;
        this.conversationAttachmentRepository = conversationAttachmentRepository;
        this.transportBindingRepository = transportBindingRepository;
        this.deliveryRecordRepository = deliveryRecordRepository;
        this.conversationAttachmentService = conversationAttachmentService;
        this.userAccountService = userAccountService;
        this.mediaStorageService = mediaStorageService;
        this.conversationProperties = conversationProperties;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
    }

    @Transactional
    public ConversationMember createConversation(AuthenticatedUser authenticatedUser, String title) {
        UserAccount owner = userAccountService.requireActiveUser(authenticatedUser.userId());
        return createConversation(owner, title);
    }

    @Transactional
    public ConversationMember createConversation(UserAccount owner, String title) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner is required");
        }

        String normalizedTitle = normalizeConversationTitle(title);

        Instant now = clock.instant();
        ConversationMember recentDuplicate = findRecentDuplicateConversation(owner, normalizedTitle, now);
        if (recentDuplicate != null) {
            return recentDuplicate;
        }

        Conversation conversation = conversationRepository.save(
            new Conversation(owner.getTenantKey(), normalizedTitle, now)
        );
        return conversationMemberRepository.save(
            new ConversationMember(conversation, owner, null, ConversationMemberRole.OWNER, now)
        );
    }

    @Transactional(readOnly = true)
    public Conversation getConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
            .orElseThrow(() -> new NotFoundException("Conversation %s not found".formatted(conversationId)));
    }

    @Transactional(readOnly = true)
    public ConversationMember requireMembership(AuthenticatedUser authenticatedUser, Long conversationId) {
        ConversationMember membership = conversationMemberRepository
            .findByConversation_IdAndUserAccount_Id(conversationId, authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Conversation %s not found".formatted(conversationId)));

        Conversation conversation = membership.getConversation();
        if (!conversation.getTenantKey().equals(authenticatedUser.tenantKey())) {
            throw new NotFoundException("Conversation %s not found".formatted(conversationId));
        }
        return membership;
    }

    @Transactional(readOnly = true)
    public Conversation getConversationAccessible(AuthenticatedUser authenticatedUser, Long conversationId) {
        return requireMembership(authenticatedUser, conversationId).getConversation();
    }

    @Transactional(readOnly = true)
    public ConversationMember requireOwnerMembership(AuthenticatedUser authenticatedUser, Long conversationId) {
        ConversationMember membership = requireMembership(authenticatedUser, conversationId);
        if (membership.getRole() != ConversationMemberRole.OWNER) {
            throw new ForbiddenException("Only conversation owner can manage this chat");
        }
        return membership;
    }

    @Transactional(readOnly = true)
    public ConversationMember requireManagerMembership(AuthenticatedUser authenticatedUser, Long conversationId) {
        ConversationMember membership = requireMembership(authenticatedUser, conversationId);
        if (membership.getRole() != ConversationMemberRole.OWNER && membership.getRole() != ConversationMemberRole.ADMIN) {
            throw new ForbiddenException("Only owner or admin can manage this chat");
        }
        return membership;
    }

    @Transactional
    public ConversationMember updateConversationProfile(
        AuthenticatedUser authenticatedUser,
        Long conversationId,
        String title,
        AvatarUpload avatarUpload,
        boolean removeAvatar
    ) {
        ConversationMember membership = requireManagerMembership(authenticatedUser, conversationId);
        Conversation conversation = membership.getConversation();

        conversation.updateTitle(normalizeConversationTitle(title));

        if (avatarUpload != null && avatarUpload.content().length > 0) {
            validateAvatarUpload(avatarUpload);
            StoredMediaFile storedMediaFile = mediaStorageService.store(
                avatarUpload.originalFilename(),
                avatarUpload.mimeType(),
                avatarUpload.content()
            );
            String previousStorageKey = conversation.getAvatarStorageKey();
            conversation.updateAvatar(
                storedMediaFile.storageKey(),
                storedMediaFile.mimeType(),
                clipFilename(storedMediaFile.originalFilename()),
                clock.instant()
            );
            if (previousStorageKey != null && !previousStorageKey.isBlank()
                && !previousStorageKey.equals(storedMediaFile.storageKey())) {
                mediaStorageService.delete(previousStorageKey);
            }
        } else if (removeAvatar) {
            String previousStorageKey = conversation.getAvatarStorageKey();
            conversation.clearAvatar();
            if (previousStorageKey != null && !previousStorageKey.isBlank()) {
                mediaStorageService.delete(previousStorageKey);
            }
        }

        conversationRepository.save(conversation);
        return membership;
    }

    @Transactional
    public void deleteConversation(AuthenticatedUser authenticatedUser, Long conversationId) {
        Conversation conversation = requireOwnerMembership(authenticatedUser, conversationId).getConversation();
        String avatarStorageKey = conversation.getAvatarStorageKey();

        List<ConversationAttachment> attachments = conversationAttachmentRepository.findAllByMessage_Conversation_Id(conversationId);
        Set<String> attachmentStorageKeys = new LinkedHashSet<>();
        for (ConversationAttachment attachment : attachments) {
            if (attachment.getStorageKey() != null && !attachment.getStorageKey().isBlank()) {
                attachmentStorageKeys.add(attachment.getStorageKey());
            }
        }

        deliveryRecordRepository.deleteAllByConversationMessage_Conversation_Id(conversationId);
        conversationAttachmentRepository.deleteAllByMessage_Conversation_Id(conversationId);
        conversationMessageRepository.clearReplyReferences(conversationId);
        conversationMessageRepository.deleteAllByConversation_Id(conversationId);
        transportBindingRepository.deleteAllByConversation_Id(conversationId);
        conversationInviteRepository.deleteAllByConversation_Id(conversationId);
        conversationMemberRepository.deleteAllByConversation_Id(conversationId);
        conversationRepository.delete(conversation);

        attachmentStorageKeys.forEach(mediaStorageService::delete);
        if (avatarStorageKey != null && !avatarStorageKey.isBlank()) {
            mediaStorageService.delete(avatarStorageKey);
        }
    }

    @Transactional(readOnly = true)
    public List<ConversationMember> listMemberships(AuthenticatedUser authenticatedUser) {
        return conversationMemberRepository.findAllByUserAccount_IdOrderByConversation_CreatedAtDesc(
            authenticatedUser.userId()
        );
    }

    @Transactional(readOnly = true)
    public List<ConversationMessage> listMessages(AuthenticatedUser authenticatedUser, Long conversationId) {
        requireMembership(authenticatedUser, conversationId);
        List<ConversationMessage> messages = conversationMessageRepository.findAllByConversation_IdOrderByCreatedAtAsc(conversationId);
        messages.forEach(this::materializeReplyPreview);
        return messages;
    }

    @Transactional
    public ConversationMessage createInternalMessage(
        AuthenticatedUser authenticatedUser,
        Long conversationId,
        String body
    ) {
        return createInternalMessage(authenticatedUser, conversationId, body, null, List.of());
    }

    @Transactional
    public ConversationMessage createInternalMessage(
        AuthenticatedUser authenticatedUser,
        Long conversationId,
        String body,
        Long replyToMessageId
    ) {
        return createInternalMessage(authenticatedUser, conversationId, body, replyToMessageId, List.of());
    }

    @Transactional
    public ConversationMessage createInternalMessage(
        AuthenticatedUser authenticatedUser,
        Long conversationId,
        String body,
        List<ConversationAttachmentDraft> attachments
    ) {
        return createInternalMessage(authenticatedUser, conversationId, body, null, attachments);
    }

    @Transactional
    public ConversationMessage createInternalMessage(
        AuthenticatedUser authenticatedUser,
        Long conversationId,
        String body,
        Long replyToMessageId,
        List<ConversationAttachmentDraft> attachments
    ) {
        Conversation conversation = requireMembership(authenticatedUser, conversationId).getConversation();
        UserAccount author = userAccountService.requireActiveUser(authenticatedUser.userId());
        ConversationMessage replyToMessage = resolveReplyToMessage(conversation.getId(), replyToMessageId);
        String normalizedBody = normalizeBody(body, attachments);
        ConversationMessage message = new ConversationMessage(
            conversation,
            BridgeTransport.INTERNAL,
            conversationId.toString(),
            null,
            author,
            String.valueOf(author.getId()),
            author.getDisplayName(),
            normalizedBody,
            replyToMessage,
            clock.instant()
        );
        ConversationMessage savedMessage = conversationMessageRepository.save(message);
        conversationAttachmentService.storeDrafts(savedMessage, attachments);
        materializeReplyPreview(savedMessage);
        return savedMessage;
    }

    @Transactional
    public ConversationMessage createExternalMessage(
        Long conversationId,
        BridgeTransport sourceTransport,
        String sourceChatId,
        String sourceMessageId,
        UserAccount authorUser,
        String authorId,
        String authorName,
        String body,
        java.time.Instant createdAt,
        List<ConversationAttachmentDraft> attachments
    ) {
        return createExternalMessage(
            conversationId,
            sourceTransport,
            sourceChatId,
            sourceMessageId,
            authorUser,
            authorId,
            authorName,
            body,
            createdAt,
            null,
            attachments
        );
    }

    @Transactional
    public ConversationMessage createExternalMessage(
        Long conversationId,
        BridgeTransport sourceTransport,
        String sourceChatId,
        String sourceMessageId,
        UserAccount authorUser,
        String authorId,
        String authorName,
        String body,
        java.time.Instant createdAt,
        ConversationMessage replyToMessage,
        List<ConversationAttachmentDraft> attachments
    ) {
        Conversation conversation = getConversation(conversationId);
        ConversationMessage message = new ConversationMessage(
            conversation,
            sourceTransport,
            normalizeNullable(sourceChatId),
            normalizeNullable(sourceMessageId),
            authorUser,
            normalizeNullable(authorId),
            normalizeAuthorName(authorName),
            normalizeBody(body, attachments),
            replyToMessage,
            createdAt == null ? clock.instant() : createdAt
        );
        ConversationMessage savedMessage = conversationMessageRepository.save(message);
        conversationAttachmentService.storeDrafts(savedMessage, attachments);
        materializeReplyPreview(savedMessage);
        return savedMessage;
    }

    @Transactional(readOnly = true)
    public List<ConversationMember> listMembers(AuthenticatedUser authenticatedUser, Long conversationId) {
        requireMembership(authenticatedUser, conversationId);
        return conversationMemberRepository.findAllByConversation_IdOrderByJoinedAtAsc(conversationId);
    }

    @Transactional
    public IssuedConversationInvite createInvite(AuthenticatedUser authenticatedUser, Long conversationId) {
        ConversationMember managerMembership = requireManagerMembership(authenticatedUser, conversationId);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(conversationProperties.inviteTtlHours(), ChronoUnit.HOURS);
        String inviteCode = generateInviteCode();

        ConversationInvite invite = conversationInviteRepository.save(
            new ConversationInvite(
                managerMembership.getConversation(),
                managerMembership.getUserAccount(),
                tokenHasher.hash(inviteCode),
                inviteCode.substring(0, Math.min(inviteCode.length(), 12)),
                false,
                now,
                expiresAt
            )
        );
        return new IssuedConversationInvite(invite, inviteCode);
    }

    @Transactional
    public ConversationMember updateMemberRole(
        AuthenticatedUser authenticatedUser,
        Long conversationId,
        Long targetUserId,
        ConversationMemberRole role
    ) {
        requireOwnerMembership(authenticatedUser, conversationId);

        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (role == ConversationMemberRole.OWNER) {
            throw new IllegalArgumentException("OWNER role cannot be assigned through this endpoint");
        }

        ConversationMember targetMembership = conversationMemberRepository
            .findByConversation_IdAndUserAccount_Id(conversationId, targetUserId)
            .orElseThrow(() -> new NotFoundException("Member %s not found".formatted(targetUserId)));

        if (targetMembership.getRole() == ConversationMemberRole.OWNER) {
            throw new IllegalArgumentException("Conversation owner role cannot be changed");
        }

        targetMembership.updateRole(role);
        return conversationMemberRepository.save(targetMembership);
    }

    @Transactional
    public ConversationInviteAcceptanceResult acceptInvite(AuthenticatedUser authenticatedUser, String inviteCode) {
        UserAccount userAccount = userAccountService.requireActiveUser(authenticatedUser.userId());
        return acceptInvite(userAccount, inviteCode);
    }

    @Transactional
    public ConversationInviteAcceptanceResult acceptInvite(UserAccount userAccount, String inviteCode) {
        if (userAccount == null || userAccount.getId() == null) {
            throw new IllegalArgumentException("userAccount is required");
        }

        ConversationInvite invite = conversationInviteRepository.findByInviteHash(tokenHasher.hash(normalizeInviteCode(inviteCode)))
            .orElseThrow(() -> new NotFoundException("Invite not found"));

        Instant now = clock.instant();
        if (!invite.isAvailableAt(now)) {
            throw new IllegalArgumentException("Invite is no longer valid");
        }
        if (!invite.getConversation().getTenantKey().equals(userAccount.getTenantKey())) {
            throw new IllegalArgumentException("Invite belongs to a different tenant");
        }

        ConversationMember existingMembership = conversationMemberRepository
            .findByConversation_IdAndUserAccount_Id(invite.getConversation().getId(), userAccount.getId())
            .orElse(null);
        if (existingMembership != null) {
            return new ConversationInviteAcceptanceResult(existingMembership, true);
        }

        ConversationMember membership = conversationMemberRepository.save(
            new ConversationMember(
                invite.getConversation(),
                userAccount,
                invite.getCreatedByUser(),
                ConversationMemberRole.MEMBER,
                now
            )
        );
        invite.markAccepted(userAccount, now);
        return new ConversationInviteAcceptanceResult(membership, false);
    }

    @Transactional(readOnly = true)
    public ResolvedConversationAvatar resolveConversationAvatar(AuthenticatedUser authenticatedUser, Long conversationId) {
        Conversation conversation = requireMembership(authenticatedUser, conversationId).getConversation();
        if (conversation.getAvatarStorageKey() == null || conversation.getAvatarStorageKey().isBlank()) {
            throw new NotFoundException("Conversation avatar not found");
        }
        if (!mediaStorageService.exists(conversation.getAvatarStorageKey())) {
            throw new NotFoundException("Conversation avatar content not found");
        }
        return new ResolvedConversationAvatar(conversation);
    }

    public String buildConversationAvatarUrl(Conversation conversation) {
        if (conversation == null || conversation.getAvatarStorageKey() == null || conversation.getAvatarStorageKey().isBlank()) {
            return null;
        }

        Instant versionSource = conversation.getAvatarUpdatedAt();
        String version = versionSource == null
            ? String.valueOf(conversation.getId())
            : String.valueOf(versionSource.toEpochMilli());
        return "/api/conversations/" + conversation.getId() + "/avatar?v=" + version;
    }

    public InputStream openAvatarStream(Conversation conversation) {
        if (conversation == null || conversation.getAvatarStorageKey() == null || conversation.getAvatarStorageKey().isBlank()) {
            throw new NotFoundException("Conversation avatar not found");
        }
        return mediaStorageService.openStream(conversation.getAvatarStorageKey());
    }

    private String normalizeAuthorName(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        return value.trim();
    }

    private ConversationMember findRecentDuplicateConversation(UserAccount owner, String normalizedTitle, Instant now) {
        Instant dedupeThreshold = now.minusSeconds(12);
        for (ConversationMember membership : conversationMemberRepository
            .findTop5ByUserAccount_IdAndRoleOrderByConversation_CreatedAtDesc(owner.getId(), ConversationMemberRole.OWNER)) {
            Conversation conversation = membership.getConversation();
            if (conversation == null || conversation.getCreatedAt() == null) {
                continue;
            }
            if (conversation.getCreatedAt().isBefore(dedupeThreshold)) {
                continue;
            }
            if (normalizeConversationTitleForCompare(conversation.getTitle()).equals(
                normalizeConversationTitleForCompare(normalizedTitle)
            )) {
                return membership;
            }
        }
        return null;
    }

    private String normalizeConversationTitle(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }

        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > MAX_CONVERSATION_TITLE_LENGTH) {
            normalized = normalized.substring(0, MAX_CONVERSATION_TITLE_LENGTH);
        }
        return normalized;
    }

    private String normalizeConversationTitleForCompare(String value) {
        return normalizeConversationTitle(value).toLowerCase(Locale.ROOT);
    }

    private void validateAvatarUpload(AvatarUpload avatarUpload) {
        String mimeType = avatarUpload.mimeType() == null ? "" : avatarUpload.mimeType().trim().toLowerCase(Locale.ROOT);
        String fileName = avatarUpload.originalFilename() == null ? "" : avatarUpload.originalFilename().trim().toLowerCase(Locale.ROOT);

        boolean imageMime = mimeType.startsWith("image/");
        boolean imageExtension = fileName.endsWith(".jpg")
            || fileName.endsWith(".jpeg")
            || fileName.endsWith(".png")
            || fileName.endsWith(".gif")
            || fileName.endsWith(".webp")
            || fileName.endsWith(".bmp")
            || fileName.endsWith(".heic")
            || fileName.endsWith(".heif")
            || fileName.endsWith(".svg");

        if (!imageMime && !imageExtension) {
            throw new IllegalArgumentException("Аватар чата должен быть изображением");
        }
    }

    private String clipFilename(String value) {
        if (value == null || value.isBlank()) {
            return "avatar";
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_CONVERSATION_AVATAR_FILENAME_LENGTH) {
            return normalized.substring(0, MAX_CONVERSATION_AVATAR_FILENAME_LENGTH);
        }
        return normalized;
    }

    private ConversationMessage resolveReplyToMessage(Long conversationId, Long replyToMessageId) {
        if (replyToMessageId == null) {
            return null;
        }

        return conversationMessageRepository.findByIdAndConversation_Id(replyToMessageId, conversationId)
            .orElseThrow(() -> new NotFoundException("Message %s not found".formatted(replyToMessageId)));
    }

    private void materializeReplyPreview(ConversationMessage message) {
        if (message == null) {
            return;
        }

        ConversationMessage replyToMessage = message.getReplyToMessage();
        if (replyToMessage == null) {
            return;
        }

        replyToMessage.getId();
        replyToMessage.getAuthorDisplayName();
        replyToMessage.getBody();
        replyToMessage.getCreatedAt();
        if (replyToMessage.getAuthorUser() != null) {
            replyToMessage.getAuthorUser().getId();
        }
        replyToMessage.getAttachments().size();
    }

    private String normalizeBody(String value, List<ConversationAttachmentDraft> attachments) {
        if (value == null || value.isBlank()) {
            if (attachments != null && !attachments.isEmpty()) {
                return "";
            }
            throw new IllegalArgumentException("body is required");
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeInviteCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("inviteCode is required");
        }
        return value.trim();
    }

    private String generateInviteCode() {
        byte[] bytes = new byte[18];
        secureRandom.nextBytes(bytes);
        return "join_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record AvatarUpload(
        String originalFilename,
        String mimeType,
        byte[] content
    ) {
    }

    public record ResolvedConversationAvatar(
        Conversation conversation
    ) {
    }
}
