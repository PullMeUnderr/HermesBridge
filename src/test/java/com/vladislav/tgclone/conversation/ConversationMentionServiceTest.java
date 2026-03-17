package com.vladislav.tgclone.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.vladislav.tgclone.account.TelegramIdentity;
import com.vladislav.tgclone.account.TelegramIdentityRepository;
import com.vladislav.tgclone.account.UserAccount;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConversationMentionServiceTest {

    @Mock
    private ConversationMemberRepository conversationMemberRepository;

    @Mock
    private TelegramIdentityRepository telegramIdentityRepository;

    private ConversationMentionService conversationMentionService;

    @BeforeEach
    void setUp() {
        conversationMentionService = new ConversationMentionService(
            conversationMemberRepository,
            telegramIdentityRepository
        );
    }

    @Test
    void resolveTelegramOutboundMentionsMapsHermesUsernameToTelegramUsername() {
        UserAccount userAccount = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(userAccount, "id", 7L);

        Conversation conversation = new Conversation("main", "Room", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);

        ConversationMember member = new ConversationMember(
            conversation,
            userAccount,
            null,
            ConversationMemberRole.MEMBER,
            Instant.EPOCH
        );

        TelegramIdentity telegramIdentity = new TelegramIdentity(
            userAccount,
            "42",
            "AliceTg",
            "100500",
            Instant.EPOCH,
            Instant.EPOCH
        );

        when(conversationMemberRepository.findAllByConversation_IdOrderByJoinedAtAsc(15L)).thenReturn(List.of(member));
        when(telegramIdentityRepository.findAllByUserAccount_IdIn(List.of(7L))).thenReturn(List.of(telegramIdentity));

        String resolved = conversationMentionService.resolveTelegramOutboundMentions(
            15L,
            "Привет, @alice. И @nobody тоже."
        );

        assertEquals("Привет, @AliceTg. И @nobody тоже.", resolved);
    }

    @Test
    void normalizeTelegramMentionsMapsTelegramUsernameBackToHermesUsername() {
        UserAccount userAccount = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(userAccount, "id", 7L);

        Conversation conversation = new Conversation("main", "Room", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);

        ConversationMember member = new ConversationMember(
            conversation,
            userAccount,
            null,
            ConversationMemberRole.MEMBER,
            Instant.EPOCH
        );

        TelegramIdentity telegramIdentity = new TelegramIdentity(
            userAccount,
            "42",
            "AliceTg",
            "100500",
            Instant.EPOCH,
            Instant.EPOCH
        );

        when(conversationMemberRepository.findAllByConversation_IdOrderByJoinedAtAsc(15L)).thenReturn(List.of(member));
        when(telegramIdentityRepository.findAllByUserAccount_IdIn(List.of(7L))).thenReturn(List.of(telegramIdentity));

        String resolved = conversationMentionService.normalizeTelegramMentions(
            15L,
            "Привет, @alicetg."
        );

        assertEquals("Привет, @alice.", resolved);
    }
}
