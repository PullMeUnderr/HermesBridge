package com.vladislav.tgclone.telegram;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.account.TelegramRegistrationService;
import com.vladislav.tgclone.bridge.MessageRelayService;
import com.vladislav.tgclone.bridge.TransportBindingService;
import com.vladislav.tgclone.conversation.Conversation;
import com.vladislav.tgclone.conversation.ConversationMember;
import com.vladislav.tgclone.conversation.ConversationMemberRole;
import com.vladislav.tgclone.conversation.ConversationService;
import com.vladislav.tgclone.conversation.ConversationInviteAcceptanceResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TelegramPollingServiceTest {

    @Mock
    private TelegramBotClient telegramBotClient;

    @Mock
    private SyncCursorRepository syncCursorRepository;

    @Mock
    private MessageRelayService messageRelayService;

    @Mock
    private TelegramRegistrationService telegramRegistrationService;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private TransportBindingService transportBindingService;

    private TelegramPrivateDialogStateService dialogStateService;
    private TelegramPollingService telegramPollingService;

    @BeforeEach
    void setUp() {
        dialogStateService = new TelegramPrivateDialogStateService();
        telegramPollingService = new TelegramPollingService(
            new TelegramProperties(true, "token", "HermesBridgeBot", 25),
            telegramBotClient,
            syncCursorRepository,
            messageRelayService,
            telegramRegistrationService,
            userAccountService,
            conversationService,
            transportBindingService,
            dialogStateService
        );
    }

    @Test
    void pollRegistersConversationFromGroupCommand() {
        UserAccount userAccount = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(userAccount, "id", 7L);

        Conversation conversation = new Conversation("main", "Hermes group", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);
        ConversationMember membership = new ConversationMember(
            conversation,
            userAccount,
            null,
            ConversationMemberRole.OWNER,
            Instant.EPOCH
        );

        TelegramUpdateDto update = new TelegramUpdateDto(
            101L,
            new TelegramMessageDto(
                501L,
                new TelegramChatDto(-100123L, "Hermes group", "supergroup"),
                new TelegramUserDto(42L, "Alice", null, "alice"),
                1_763_650_000L,
                "/registerchat"
            ),
            null
        );

        when(syncCursorRepository.findById("telegram-updates")).thenReturn(Optional.empty());
        when(telegramBotClient.fetchUpdates(0L)).thenReturn(List.of(update));
        when(userAccountService.findByTelegramUserId("42")).thenReturn(Optional.of(userAccount));
        when(transportBindingService.createConversationWithTelegramBinding(userAccount, "Hermes group", "-100123"))
            .thenReturn(membership);

        telegramPollingService.poll();

        verify(telegramBotClient).sendMessage(eq("-100123"), contains("Готово, группа зарегистрирована."));
        verify(syncCursorRepository).save(any(SyncCursor.class));
    }

    @Test
    void pollCreatesConversationFromPrivateButtonFlow() {
        UserAccount userAccount = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(userAccount, "id", 7L);

        Conversation conversation = new Conversation("main", "Новый чат", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);
        ConversationMember membership = new ConversationMember(
            conversation,
            userAccount,
            null,
            ConversationMemberRole.OWNER,
            Instant.EPOCH
        );

        TelegramUpdateDto buttonUpdate = new TelegramUpdateDto(
            101L,
            new TelegramMessageDto(
                501L,
                new TelegramChatDto(100500L, null, "private"),
                new TelegramUserDto(42L, "Alice", null, "alice"),
                1_763_650_000L,
                "Создать чат"
            ),
            null
        );
        TelegramUpdateDto titleUpdate = new TelegramUpdateDto(
            102L,
            new TelegramMessageDto(
                502L,
                new TelegramChatDto(100500L, null, "private"),
                new TelegramUserDto(42L, "Alice", null, "alice"),
                1_763_650_001L,
                "Новый чат"
            ),
            null
        );

        when(syncCursorRepository.findById("telegram-updates")).thenReturn(Optional.empty());
        when(telegramBotClient.fetchUpdates(0L)).thenReturn(List.of(buttonUpdate, titleUpdate));
        when(userAccountService.findByTelegramUserId("42")).thenReturn(Optional.of(userAccount));
        when(conversationService.createConversation(userAccount, "Новый чат")).thenReturn(membership);

        telegramPollingService.poll();

        verify(telegramBotClient).sendMessage(eq("100500"), contains("Пришли название нового чата."), any());
        verify(telegramBotClient).sendMessage(eq("100500"), contains("Чат создан."), any());
        verify(syncCursorRepository).save(any(SyncCursor.class));
    }

    @Test
    void pollJoinsConversationFromPrivateLoginButtonFlow() {
        UserAccount userAccount = new UserAccount("main", "alice", "Alice", true, Instant.EPOCH);
        ReflectionTestUtils.setField(userAccount, "id", 7L);

        Conversation conversation = new Conversation("main", "Hermes room", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);
        ConversationMember membership = new ConversationMember(
            conversation,
            userAccount,
            null,
            ConversationMemberRole.MEMBER,
            Instant.EPOCH
        );

        TelegramUpdateDto buttonUpdate = new TelegramUpdateDto(
            101L,
            new TelegramMessageDto(
                501L,
                new TelegramChatDto(100500L, null, "private"),
                new TelegramUserDto(42L, "Alice", null, "alice"),
                1_763_650_000L,
                "Вход"
            ),
            null
        );
        TelegramUpdateDto codeUpdate = new TelegramUpdateDto(
            102L,
            new TelegramMessageDto(
                502L,
                new TelegramChatDto(100500L, null, "private"),
                new TelegramUserDto(42L, "Alice", null, "alice"),
                1_763_650_001L,
                "join_testcode"
            ),
            null
        );

        when(syncCursorRepository.findById("telegram-updates")).thenReturn(Optional.empty());
        when(telegramBotClient.fetchUpdates(0L)).thenReturn(List.of(buttonUpdate, codeUpdate));
        when(userAccountService.findByTelegramUserId("42")).thenReturn(Optional.of(userAccount));
        when(conversationService.acceptInvite(userAccount, "join_testcode"))
            .thenReturn(new ConversationInviteAcceptanceResult(membership, false));

        telegramPollingService.poll();

        verify(telegramBotClient).sendMessage(eq("100500"), contains("Пришли invite-код"), any());
        verify(telegramBotClient).sendMessage(eq("100500"), contains("Готово, ты вступил в чат."), any());
    }
}
