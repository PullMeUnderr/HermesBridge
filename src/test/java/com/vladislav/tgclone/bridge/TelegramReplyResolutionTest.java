package com.vladislav.tgclone.bridge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.conversation.Conversation;
import com.vladislav.tgclone.conversation.ConversationEventPublisher;
import com.vladislav.tgclone.conversation.ConversationMentionService;
import com.vladislav.tgclone.conversation.ConversationMessage;
import com.vladislav.tgclone.conversation.ConversationMessageRepository;
import com.vladislav.tgclone.conversation.ConversationService;
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
class TelegramReplyResolutionTest {

    @Mock
    private TransportBindingRepository transportBindingRepository;

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    @Mock
    private DeliveryRecordRepository deliveryRecordRepository;

    @Mock
    private ConversationService conversationService;

    @Mock
    private ConversationEventPublisher conversationEventPublisher;

    @Mock
    private ConversationMentionService conversationMentionService;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private AsyncMessageFanOutService asyncMessageFanOutService;

    private MessageRelayService messageRelayService;

    @BeforeEach
    void setUp() {
        messageRelayService = new MessageRelayService(
            transportBindingRepository,
            conversationMessageRepository,
            deliveryRecordRepository,
            conversationService,
            conversationEventPublisher,
            conversationMentionService,
            userAccountService,
            asyncMessageFanOutService
        );
    }

    @Test
    void processTelegramInboundResolvesReplyFromExistingTelegramDeliveryRecord() {
        Conversation conversation = new Conversation("main", "Room", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 15L);

        TransportBinding sourceBinding = new TransportBinding(
            conversation,
            BridgeTransport.TELEGRAM,
            "-100777",
            true,
            Instant.EPOCH
        );

        ConversationMessage repliedMessage = new ConversationMessage(
            conversation,
            BridgeTransport.INTERNAL,
            "15",
            null,
            null,
            "7",
            "Alice",
            "Первое сообщение",
            Instant.EPOCH
        );
        ReflectionTestUtils.setField(repliedMessage, "id", 44L);

        DeliveryRecord deliveryRecord = new DeliveryRecord(
            repliedMessage,
            BridgeTransport.TELEGRAM,
            "-100777",
            "900",
            Instant.EPOCH
        );

        ConversationMessage saved = new ConversationMessage(
            conversation,
            BridgeTransport.TELEGRAM,
            "-100777",
            "901",
            null,
            "42",
            "Bob",
            "Ответ",
            repliedMessage,
            Instant.EPOCH
        );

        when(transportBindingRepository.findByTransportAndExternalChatIdAndActiveTrue(BridgeTransport.TELEGRAM, "-100777"))
            .thenReturn(Optional.of(sourceBinding));
        when(conversationMessageRepository.findBySourceTransportAndSourceChatIdAndSourceMessageId(
            BridgeTransport.TELEGRAM,
            "-100777",
            "901"
        )).thenReturn(Optional.empty());
        when(deliveryRecordRepository.findByTargetTransportAndTargetChatIdAndTargetMessageId(
            BridgeTransport.TELEGRAM,
            "-100777",
            "900"
        )).thenReturn(Optional.of(deliveryRecord));
        when(conversationMentionService.normalizeTelegramMentions(15L, "Ответ")).thenReturn("Ответ");
        when(conversationService.createExternalMessage(
            eq(15L),
            eq(BridgeTransport.TELEGRAM),
            eq("-100777"),
            eq("901"),
            eq(null),
            eq("42"),
            eq("Bob"),
            eq("Ответ"),
            eq(Instant.parse("2026-03-17T10:00:00Z")),
            eq(repliedMessage),
            any()
        )).thenReturn(saved);

        messageRelayService.processTelegramInbound(new TelegramInboundEnvelope(
            "-100777",
            "901",
            "42",
            "Bob",
            "Ответ",
            Instant.parse("2026-03-17T10:00:00Z"),
            "900",
            List.of()
        ));

        verify(conversationService).createExternalMessage(
            eq(15L),
            eq(BridgeTransport.TELEGRAM),
            eq("-100777"),
            eq("901"),
            eq(null),
            eq("42"),
            eq("Bob"),
            eq("Ответ"),
            eq(Instant.parse("2026-03-17T10:00:00Z")),
            eq(repliedMessage),
            any()
        );
    }
}
