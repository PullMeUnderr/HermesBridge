package com.vladislav.tgclone.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vladislav.tgclone.account.TokenHasher;
import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.bridge.DeliveryRecordRepository;
import com.vladislav.tgclone.bridge.TransportBindingRepository;
import com.vladislav.tgclone.media.MediaStorageService;
import com.vladislav.tgclone.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    @Mock
    private ConversationMemberRepository conversationMemberRepository;

    @Mock
    private ConversationInviteRepository conversationInviteRepository;

    @Mock
    private ConversationAttachmentRepository conversationAttachmentRepository;

    @Mock
    private TransportBindingRepository transportBindingRepository;

    @Mock
    private DeliveryRecordRepository deliveryRecordRepository;

    @Mock
    private ConversationAttachmentService conversationAttachmentService;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private MediaStorageService mediaStorageService;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
            conversationRepository,
            conversationMessageRepository,
            conversationMemberRepository,
            conversationInviteRepository,
            conversationAttachmentRepository,
            transportBindingRepository,
            deliveryRecordRepository,
            conversationAttachmentService,
            userAccountService,
            mediaStorageService,
            new ConversationProperties(168),
            new TokenHasher(),
            Clock.fixed(Instant.parse("2026-03-16T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createConversationAddsOwnerMembership() {
        UserAccount owner = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(owner, "id", 7L);
        AuthenticatedUser authenticatedUser = AuthenticatedUser.from(owner);

        when(userAccountService.requireActiveUser(7L)).thenReturn(owner);
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            ReflectionTestUtils.setField(conversation, "id", 15L);
            return conversation;
        });
        when(conversationMemberRepository.save(any(ConversationMember.class))).thenAnswer(invocation -> {
            ConversationMember membership = invocation.getArgument(0);
            ReflectionTestUtils.setField(membership, "id", 21L);
            return membership;
        });

        ConversationMember membership = conversationService.createConversation(authenticatedUser, "Main chat");

        assertEquals(15L, membership.getConversation().getId());
        assertEquals("Main chat", membership.getConversation().getTitle());
        assertEquals(ConversationMemberRole.OWNER, membership.getRole());
        assertEquals(7L, membership.getUserAccount().getId());
    }

    @Test
    void createConversationReturnsRecentDuplicateForSameOwnerAndTitle() {
        UserAccount owner = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(owner, "id", 7L);
        AuthenticatedUser authenticatedUser = AuthenticatedUser.from(owner);

        Conversation conversation = new Conversation("main", "Main chat", Instant.parse("2026-03-16T11:59:55Z"));
        ReflectionTestUtils.setField(conversation, "id", 15L);
        ConversationMember existingMembership = new ConversationMember(
            conversation,
            owner,
            null,
            ConversationMemberRole.OWNER,
            Instant.parse("2026-03-16T11:59:55Z")
        );

        when(userAccountService.requireActiveUser(7L)).thenReturn(owner);
        when(conversationMemberRepository.findTop5ByUserAccount_IdAndRoleOrderByConversation_CreatedAtDesc(
            7L,
            ConversationMemberRole.OWNER
        )).thenReturn(List.of(existingMembership));

        ConversationMember membership = conversationService.createConversation(authenticatedUser, "  Main   chat ");

        assertSame(existingMembership, membership);
        verify(conversationRepository, never()).save(any(Conversation.class));
        verify(conversationMemberRepository, never()).save(any(ConversationMember.class));
    }

    @Test
    void createInternalMessageLinksReplyToExistingMessage() {
        UserAccount author = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(author, "id", 7L);
        AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);

        Conversation conversation = new Conversation("main", "Main chat", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);

        ConversationMember membership = new ConversationMember(
            conversation,
            author,
            null,
            ConversationMemberRole.OWNER,
            Instant.EPOCH
        );
        ConversationMessage repliedMessage = new ConversationMessage(
            conversation,
            com.vladislav.tgclone.bridge.BridgeTransport.INTERNAL,
            "15",
            null,
            author,
            "7",
            "Alice",
            "Первое",
            Instant.EPOCH
        );
        ReflectionTestUtils.setField(repliedMessage, "id", 99L);

        when(conversationMemberRepository.findByConversation_IdAndUserAccount_Id(15L, 7L))
            .thenReturn(Optional.of(membership));
        when(userAccountService.requireActiveUser(7L)).thenReturn(author);
        when(conversationMessageRepository.findByIdAndConversation_Id(99L, 15L))
            .thenReturn(Optional.of(repliedMessage));
        when(conversationMessageRepository.save(any(ConversationMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConversationMessage created = conversationService.createInternalMessage(
            authenticatedUser,
            15L,
            "Ответ",
            99L,
            List.of()
        );

        assertSame(repliedMessage, created.getReplyToMessage());
        verify(conversationAttachmentService).storeDrafts(created, List.of());
    }

    @Test
    void acceptInviteCreatesMembershipAndMarksInviteAccepted() {
        UserAccount owner = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(owner, "id", 7L);
        UserAccount invitedUser = new UserAccount("main", "bob", "Bob", true, Instant.EPOCH);
        ReflectionTestUtils.setField(invitedUser, "id", 8L);

        Conversation conversation = new Conversation("main", "Main chat", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);

        String inviteCode = "join_testcode";
        ConversationInvite invite = new ConversationInvite(
            conversation,
            owner,
            new TokenHasher().hash(inviteCode),
            inviteCode.substring(0, 12),
            false,
            Instant.parse("2026-03-16T11:00:00Z"),
            Instant.parse("2026-03-17T12:00:00Z")
        );

        when(conversationInviteRepository.findByInviteHash(new TokenHasher().hash(inviteCode)))
            .thenReturn(Optional.of(invite));
        when(conversationMemberRepository.findByConversation_IdAndUserAccount_Id(15L, 8L))
            .thenReturn(Optional.empty());
        when(conversationMemberRepository.save(any(ConversationMember.class))).thenAnswer(invocation -> {
            ConversationMember membership = invocation.getArgument(0);
            ReflectionTestUtils.setField(membership, "id", 33L);
            return membership;
        });

        ConversationInviteAcceptanceResult result = conversationService.acceptInvite(invitedUser, inviteCode);

        assertFalse(result.alreadyMember());
        assertEquals(ConversationMemberRole.MEMBER, result.membership().getRole());
        assertEquals(8L, result.membership().getUserAccount().getId());
        assertEquals(7L, result.membership().getInvitedByUser().getId());
        assertEquals(15L, result.membership().getConversation().getId());
        assertEquals(8L, invite.getAcceptedByUser().getId());
        assertNotNull(invite.getAcceptedAt());
    }

    @Test
    void acceptInviteReturnsExistingMembershipWhenUserAlreadyJoined() {
        UserAccount owner = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(owner, "id", 7L);
        UserAccount invitedUser = new UserAccount("main", "bob", "Bob", true, Instant.EPOCH);
        ReflectionTestUtils.setField(invitedUser, "id", 8L);

        Conversation conversation = new Conversation("main", "Main chat", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);

        String inviteCode = "join_existing";
        ConversationInvite invite = new ConversationInvite(
            conversation,
            owner,
            new TokenHasher().hash(inviteCode),
            inviteCode.substring(0, 12),
            false,
            Instant.parse("2026-03-16T11:00:00Z"),
            Instant.parse("2026-03-17T12:00:00Z")
        );
        ConversationMember existingMembership = new ConversationMember(
            conversation,
            invitedUser,
            owner,
            ConversationMemberRole.MEMBER,
            Instant.parse("2026-03-16T11:30:00Z")
        );
        ReflectionTestUtils.setField(existingMembership, "id", 33L);

        when(conversationInviteRepository.findByInviteHash(new TokenHasher().hash(inviteCode)))
            .thenReturn(Optional.of(invite));
        when(conversationMemberRepository.findByConversation_IdAndUserAccount_Id(15L, 8L))
            .thenReturn(Optional.of(existingMembership));

        ConversationInviteAcceptanceResult result = conversationService.acceptInvite(invitedUser, inviteCode);

        assertTrue(result.alreadyMember());
        assertEquals(33L, result.membership().getId());
        verify(conversationMemberRepository, never()).save(any(ConversationMember.class));
    }

    @Test
    void updateMemberRolePromotesMemberToAdmin() {
        UserAccount owner = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(owner, "id", 7L);
        AuthenticatedUser authenticatedUser = AuthenticatedUser.from(owner);

        Conversation conversation = new Conversation("main", "Main chat", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);

        ConversationMember ownerMembership = new ConversationMember(
            conversation,
            owner,
            null,
            ConversationMemberRole.OWNER,
            Instant.EPOCH
        );

        UserAccount memberUser = new UserAccount("main", "bob", "Bob", true, Instant.EPOCH);
        ReflectionTestUtils.setField(memberUser, "id", 8L);
        ConversationMember memberMembership = new ConversationMember(
            conversation,
            memberUser,
            owner,
            ConversationMemberRole.MEMBER,
            Instant.EPOCH
        );

        when(conversationMemberRepository.findByConversation_IdAndUserAccount_Id(15L, 7L))
            .thenReturn(Optional.of(ownerMembership));
        when(conversationMemberRepository.findByConversation_IdAndUserAccount_Id(15L, 8L))
            .thenReturn(Optional.of(memberMembership));
        when(conversationMemberRepository.save(memberMembership)).thenReturn(memberMembership);

        ConversationMember updatedMembership = conversationService.updateMemberRole(
            authenticatedUser,
            15L,
            8L,
            ConversationMemberRole.ADMIN
        );

        assertSame(memberMembership, updatedMembership);
        assertEquals(ConversationMemberRole.ADMIN, updatedMembership.getRole());
    }

    @Test
    void createInviteAllowsAdminMembership() {
        UserAccount admin = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(admin, "id", 7L);
        AuthenticatedUser authenticatedUser = AuthenticatedUser.from(admin);

        Conversation conversation = new Conversation("main", "Main chat", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);

        ConversationMember adminMembership = new ConversationMember(
            conversation,
            admin,
            null,
            ConversationMemberRole.ADMIN,
            Instant.EPOCH
        );

        when(conversationMemberRepository.findByConversation_IdAndUserAccount_Id(15L, 7L))
            .thenReturn(Optional.of(adminMembership));
        when(conversationInviteRepository.save(any(ConversationInvite.class))).thenAnswer(invocation -> {
            ConversationInvite invite = invocation.getArgument(0);
            ReflectionTestUtils.setField(invite, "id", 91L);
            return invite;
        });

        IssuedConversationInvite issuedInvite = conversationService.createInvite(authenticatedUser, 15L);

        assertEquals(91L, issuedInvite.invite().getId());
        assertEquals(15L, issuedInvite.invite().getConversation().getId());
        assertTrue(issuedInvite.inviteCode().startsWith("join_"));
        assertEquals(7L, issuedInvite.invite().getCreatedByUser().getId());
    }
}
