package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.account.TokenHasher;
import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.common.ForbiddenException;
import com.vladislav.tgclone.bridge.BridgeTransport;
import com.vladislav.tgclone.common.NotFoundException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import com.vladislav.tgclone.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationInviteRepository conversationInviteRepository;
    private final ConversationAttachmentService conversationAttachmentService;
    private final UserAccountService userAccountService;
    private final ConversationProperties conversationProperties;
    private final TokenHasher tokenHasher;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public ConversationService(
        ConversationRepository conversationRepository,
        ConversationMessageRepository conversationMessageRepository,
        ConversationMemberRepository conversationMemberRepository,
        ConversationInviteRepository conversationInviteRepository,
        ConversationAttachmentService conversationAttachmentService,
        UserAccountService userAccountService,
        ConversationProperties conversationProperties,
        TokenHasher tokenHasher,
        Clock clock
    ) {
        this.conversationRepository = conversationRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.conversationInviteRepository = conversationInviteRepository;
        this.conversationAttachmentService = conversationAttachmentService;
        this.userAccountService = userAccountService;
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
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }

        Instant now = clock.instant();
        Conversation conversation = conversationRepository.save(
            new Conversation(owner.getTenantKey(), title.trim(), now)
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

    @Transactional(readOnly = true)
    public List<ConversationMember> listMemberships(AuthenticatedUser authenticatedUser) {
        return conversationMemberRepository.findAllByUserAccount_IdOrderByConversation_CreatedAtDesc(
            authenticatedUser.userId()
        );
    }

    @Transactional(readOnly = true)
    public List<ConversationMessage> listMessages(AuthenticatedUser authenticatedUser, Long conversationId) {
        requireMembership(authenticatedUser, conversationId);
        return conversationMessageRepository.findAllByConversation_IdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional
    public ConversationMessage createInternalMessage(
        AuthenticatedUser authenticatedUser,
        Long conversationId,
        String body
    ) {
        return createInternalMessage(authenticatedUser, conversationId, body, List.of());
    }

    @Transactional
    public ConversationMessage createInternalMessage(
        AuthenticatedUser authenticatedUser,
        Long conversationId,
        String body,
        List<ConversationAttachmentDraft> attachments
    ) {
        Conversation conversation = requireMembership(authenticatedUser, conversationId).getConversation();
        UserAccount author = userAccountService.requireActiveUser(authenticatedUser.userId());
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
            clock.instant()
        );
        ConversationMessage savedMessage = conversationMessageRepository.save(message);
        conversationAttachmentService.storeDrafts(savedMessage, attachments);
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
            createdAt == null ? clock.instant() : createdAt
        );
        ConversationMessage savedMessage = conversationMessageRepository.save(message);
        conversationAttachmentService.storeDrafts(savedMessage, attachments);
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

    private String normalizeAuthorName(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        return value.trim();
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
}
