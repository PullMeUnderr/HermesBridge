package com.vladislav.tgclone.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.conversation.Conversation;
import com.vladislav.tgclone.conversation.ConversationMember;
import com.vladislav.tgclone.conversation.ConversationMemberRole;
import com.vladislav.tgclone.conversation.ConversationService;
import com.vladislav.tgclone.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransportBindingServiceTest {

    @Mock
    private TransportBindingRepository transportBindingRepository;

    @Mock
    private ConversationService conversationService;

    private TransportBindingService transportBindingService;

    @BeforeEach
    void setUp() {
        transportBindingService = new TransportBindingService(
            transportBindingRepository,
            conversationService,
            Clock.fixed(Instant.parse("2026-03-16T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createTelegramBindingCreatesActiveBindingForManager() {
        UserAccount userAccount = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(userAccount, "id", 7L);
        AuthenticatedUser authenticatedUser = AuthenticatedUser.from(userAccount);

        Conversation conversation = new Conversation("main", "Main chat", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);
        ConversationMember membership = new ConversationMember(
            conversation,
            userAccount,
            null,
            ConversationMemberRole.OWNER,
            Instant.EPOCH
        );

        when(conversationService.requireManagerMembership(authenticatedUser, 15L)).thenReturn(membership);
        when(transportBindingRepository.findAllByTransportAndExternalChatId(BridgeTransport.TELEGRAM, "-100500"))
            .thenReturn(List.of());
        when(transportBindingRepository.save(any(TransportBinding.class))).thenAnswer(invocation -> {
            TransportBinding binding = invocation.getArgument(0);
            ReflectionTestUtils.setField(binding, "id", 21L);
            return binding;
        });

        TransportBinding binding = transportBindingService.createTelegramBinding(userAccount, 15L, "-100500");

        assertEquals(21L, binding.getId());
        assertEquals(15L, binding.getConversation().getId());
        assertEquals("-100500", binding.getExternalChatId());
        assertEquals(BridgeTransport.TELEGRAM, binding.getTransport());
    }

    @Test
    void createConversationWithTelegramBindingRejectsAlreadyConnectedGroup() {
        UserAccount owner = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(owner, "id", 7L);

        Conversation conversation = new Conversation("main", "Existing", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);
        ConversationMember membership = new ConversationMember(
            conversation,
            owner,
            null,
            ConversationMemberRole.OWNER,
            Instant.EPOCH
        );

        Conversation otherConversation = new Conversation("main", "Other", Instant.EPOCH);
        ReflectionTestUtils.setField(otherConversation, "id", 20L);
        TransportBinding existingBinding = new TransportBinding(
            otherConversation,
            BridgeTransport.TELEGRAM,
            "-100500",
            true,
            Instant.EPOCH
        );

        when(conversationService.createConversation(owner, "Existing")).thenReturn(membership);
        when(transportBindingRepository.findAllByTransportAndExternalChatId(BridgeTransport.TELEGRAM, "-100500"))
            .thenReturn(List.of(existingBinding));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> transportBindingService.createConversationWithTelegramBinding(owner, "Existing", "-100500")
        );

        assertEquals("This Telegram chat is already connected to another conversation", exception.getMessage());
    }
}
