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
import com.vladislav.tgclone.conversation.ConversationAttachmentDraft;
import com.vladislav.tgclone.conversation.ConversationAttachmentKind;
import com.vladislav.tgclone.conversation.ConversationMember;
import com.vladislav.tgclone.conversation.ConversationMemberRole;
import com.vladislav.tgclone.conversation.ConversationService;
import com.vladislav.tgclone.conversation.ConversationInviteAcceptanceResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
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
            telegramMessage(
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
            telegramMessage(
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
            telegramMessage(
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
            telegramMessage(
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
            telegramMessage(
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

    @Test
    void pollProcessesInboundVideoAndVoiceAttachments() {
        TelegramUpdateDto videoUpdate = new TelegramUpdateDto(
            103L,
            new TelegramMessageDto(
                700L,
                new TelegramChatDto(-100123L, "Hermes group", "supergroup"),
                new TelegramUserDto(42L, "Alice", null, "alice"),
                1_763_650_100L,
                null,
                "video caption",
                null,
                null,
                null,
                new TelegramVideoDto("video-file", "video-unique", "clip.mp4", "video/mp4", 5_000L),
                null,
                null
            ),
            null
        );
        TelegramUpdateDto voiceUpdate = new TelegramUpdateDto(
            104L,
            new TelegramMessageDto(
                701L,
                new TelegramChatDto(-100123L, "Hermes group", "supergroup"),
                new TelegramUserDto(42L, "Alice", null, "alice"),
                1_763_650_101L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new TelegramVoiceDto("voice-file", "voice-unique", "audio/ogg", 2_000L)
            ),
            null
        );
        TelegramUpdateDto videoNoteUpdate = new TelegramUpdateDto(
            105L,
            new TelegramMessageDto(
                702L,
                new TelegramChatDto(-100123L, "Hermes group", "supergroup"),
                new TelegramUserDto(42L, "Alice", null, "alice"),
                1_763_650_102L,
                null,
                null,
                null,
                null,
                null,
                null,
                new TelegramVideoNoteDto("video-note-file", "video-note-unique", 3_000L),
                null
            ),
            null
        );

        when(syncCursorRepository.findById("telegram-updates")).thenReturn(Optional.empty());
        when(telegramBotClient.fetchUpdates(0L)).thenReturn(List.of(videoUpdate, voiceUpdate, videoNoteUpdate));
        when(telegramBotClient.downloadAttachment(
            "video-file",
            ConversationAttachmentKind.VIDEO,
            "clip.mp4",
            "video/mp4",
            5_000L
        )).thenReturn(new ConversationAttachmentDraft(
            ConversationAttachmentKind.VIDEO,
            "clip.mp4",
            "video/mp4",
            5_000L,
            new byte[] {1, 2, 3}
        ));
        when(telegramBotClient.downloadAttachment(
            "voice-file",
            ConversationAttachmentKind.VOICE,
            "telegram-voice-701.ogg",
            "audio/ogg",
            2_000L
        )).thenReturn(new ConversationAttachmentDraft(
            ConversationAttachmentKind.VOICE,
            "telegram-voice-701.ogg",
            "audio/ogg",
            2_000L,
            new byte[] {4, 5, 6}
        ));
        when(telegramBotClient.downloadAttachment(
            "video-note-file",
            ConversationAttachmentKind.VIDEO_NOTE,
            "telegram-video-note-702.mp4",
            "video/mp4",
            3_000L
        )).thenReturn(new ConversationAttachmentDraft(
            ConversationAttachmentKind.VIDEO_NOTE,
            "telegram-video-note-702.mp4",
            "video/mp4",
            3_000L,
            new byte[] {7, 8, 9}
        ));

        telegramPollingService.poll();

        ArgumentCaptor<com.vladislav.tgclone.bridge.TelegramInboundEnvelope> envelopeCaptor =
            ArgumentCaptor.forClass(com.vladislav.tgclone.bridge.TelegramInboundEnvelope.class);
        verify(messageRelayService, org.mockito.Mockito.times(3)).processTelegramInbound(envelopeCaptor.capture());
        List<com.vladislav.tgclone.bridge.TelegramInboundEnvelope> envelopes = envelopeCaptor.getAllValues();

        org.junit.jupiter.api.Assertions.assertEquals(ConversationAttachmentKind.VIDEO, envelopes.get(0).attachments().getFirst().kind());
        org.junit.jupiter.api.Assertions.assertEquals("video caption", envelopes.get(0).body());
        org.junit.jupiter.api.Assertions.assertEquals(ConversationAttachmentKind.VOICE, envelopes.get(1).attachments().getFirst().kind());
        org.junit.jupiter.api.Assertions.assertEquals("", envelopes.get(1).body());
        org.junit.jupiter.api.Assertions.assertEquals(ConversationAttachmentKind.VIDEO_NOTE, envelopes.get(2).attachments().getFirst().kind());
    }

    @Test
    void pollAggregatesInboundMediaGroupIntoSingleEnvelope() {
        TelegramUpdateDto firstAlbumUpdate = new TelegramUpdateDto(
            106L,
            new TelegramMessageDto(
                800L,
                new TelegramChatDto(-100123L, "Hermes group", "supergroup"),
                new TelegramUserDto(42L, "Alice", null, "alice"),
                1_763_650_200L,
                null,
                "album caption",
                "album-1",
                List.of(new TelegramPhotoSizeDto("photo-1", "photo-u1", 640, 640, 1_000L)),
                null,
                null,
                null,
                null
            ),
            null
        );
        TelegramUpdateDto secondAlbumUpdate = new TelegramUpdateDto(
            107L,
            new TelegramMessageDto(
                801L,
                new TelegramChatDto(-100123L, "Hermes group", "supergroup"),
                new TelegramUserDto(42L, "Alice", null, "alice"),
                1_763_650_201L,
                null,
                null,
                "album-1",
                null,
                null,
                new TelegramVideoDto("video-2", "video-u2", "album.mp4", "video/mp4", 5_000L),
                null,
                null
            ),
            null
        );

        when(syncCursorRepository.findById("telegram-updates")).thenReturn(Optional.empty());
        when(telegramBotClient.fetchUpdates(0L)).thenReturn(List.of(firstAlbumUpdate, secondAlbumUpdate));
        when(telegramBotClient.downloadAttachment(
            "photo-1",
            ConversationAttachmentKind.PHOTO,
            "telegram-photo-800.jpg",
            "image/jpeg",
            1_000L
        )).thenReturn(new ConversationAttachmentDraft(
            ConversationAttachmentKind.PHOTO,
            "telegram-photo-800.jpg",
            "image/jpeg",
            1_000L,
            new byte[] {1}
        ));
        when(telegramBotClient.downloadAttachment(
            "video-2",
            ConversationAttachmentKind.VIDEO,
            "album.mp4",
            "video/mp4",
            5_000L
        )).thenReturn(new ConversationAttachmentDraft(
            ConversationAttachmentKind.VIDEO,
            "album.mp4",
            "video/mp4",
            5_000L,
            new byte[] {2}
        ));

        telegramPollingService.poll();

        ArgumentCaptor<com.vladislav.tgclone.bridge.TelegramInboundEnvelope> envelopeCaptor =
            ArgumentCaptor.forClass(com.vladislav.tgclone.bridge.TelegramInboundEnvelope.class);
        verify(messageRelayService).processTelegramInbound(envelopeCaptor.capture());
        com.vladislav.tgclone.bridge.TelegramInboundEnvelope envelope = envelopeCaptor.getValue();

        org.junit.jupiter.api.Assertions.assertEquals("album:album-1", envelope.externalMessageId());
        org.junit.jupiter.api.Assertions.assertEquals("album caption", envelope.body());
        org.junit.jupiter.api.Assertions.assertEquals(2, envelope.attachments().size());
        org.junit.jupiter.api.Assertions.assertEquals(ConversationAttachmentKind.PHOTO, envelope.attachments().get(0).kind());
        org.junit.jupiter.api.Assertions.assertEquals(ConversationAttachmentKind.VIDEO, envelope.attachments().get(1).kind());
    }

    private TelegramMessageDto telegramMessage(
        Long messageId,
        TelegramChatDto chat,
        TelegramUserDto from,
        Long date,
        String text
    ) {
        return new TelegramMessageDto(messageId, chat, from, date, text, null, null, null, null, null, null, null);
    }
}
