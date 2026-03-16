package com.vladislav.tgclone.bridge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.conversation.Conversation;
import com.vladislav.tgclone.conversation.ConversationEventPublisher;
import com.vladislav.tgclone.conversation.ConversationMessage;
import com.vladislav.tgclone.conversation.ConversationMessageRepository;
import com.vladislav.tgclone.conversation.ConversationService;
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
class MessageRelayServiceTest {

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
    private UserAccountService userAccountService;

    @Mock
    private DeliveryGateway telegramGateway;

    private MessageRelayService messageRelayService;

    @BeforeEach
    void setUp() {
        when(telegramGateway.transport()).thenReturn(BridgeTransport.TELEGRAM);
        messageRelayService = new MessageRelayService(
            transportBindingRepository,
            conversationMessageRepository,
            deliveryRecordRepository,
            conversationService,
            conversationEventPublisher,
            userAccountService,
            List.of(telegramGateway),
            Clock.fixed(Instant.parse("2026-03-16T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void relayInternalMessagePublishesAndDeliversToTelegramBindings() {
        Conversation conversation = new Conversation("demo", "Main", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 1L);

        ConversationMessage message = new ConversationMessage(
            conversation,
            BridgeTransport.INTERNAL,
            "1",
            null,
            null,
            "vlad",
            "Vlad",
            "Hello",
            Instant.EPOCH
        );
        ReflectionTestUtils.setField(message, "id", 10L);

        TransportBinding binding = new TransportBinding(
            conversation,
            BridgeTransport.TELEGRAM,
            "-100100",
            true,
            Instant.EPOCH
        );
        ReflectionTestUtils.setField(binding, "id", 5L);

        when(transportBindingRepository.findAllByConversation_IdAndActiveTrue(1L)).thenReturn(List.of(binding));
        when(deliveryRecordRepository.existsByConversationMessage_IdAndTargetTransportAndTargetChatId(
            10L,
            BridgeTransport.TELEGRAM,
            "-100100"
        )).thenReturn(false);
        when(telegramGateway.deliver(binding, message)).thenReturn("900");

        messageRelayService.relayInternalMessage(message);

        verify(conversationEventPublisher).publish(message);
        verify(telegramGateway).deliver(binding, message);
        verify(deliveryRecordRepository).save(any(DeliveryRecord.class));
    }

    @Test
    void processTelegramInboundSkipsDuplicateMessages() {
        Conversation conversation = new Conversation("demo", "Main", Instant.EPOCH);
        ReflectionTestUtils.setField(conversation, "id", 1L);

        TransportBinding sourceBinding = new TransportBinding(
            conversation,
            BridgeTransport.TELEGRAM,
            "-100100",
            true,
            Instant.EPOCH
        );
        ReflectionTestUtils.setField(sourceBinding, "id", 7L);

        ConversationMessage existingMessage = new ConversationMessage(
            conversation,
            BridgeTransport.TELEGRAM,
            "-100100",
            "500",
            null,
            "42",
            "Alice",
            "Hi",
            Instant.EPOCH
        );

        when(transportBindingRepository.findByTransportAndExternalChatIdAndActiveTrue(
            BridgeTransport.TELEGRAM,
            "-100100"
        )).thenReturn(Optional.of(sourceBinding));
        when(conversationMessageRepository.findBySourceTransportAndSourceChatIdAndSourceMessageId(
            BridgeTransport.TELEGRAM,
            "-100100",
            "500"
        )).thenReturn(Optional.of(existingMessage));

        messageRelayService.processTelegramInbound(new TelegramInboundEnvelope(
            "-100100",
            "500",
            "42",
            "Alice",
            "Hi",
            Instant.EPOCH,
            List.of()
        ));

        verify(conversationService, never()).createExternalMessage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(conversationEventPublisher, never()).publish(any());
        verify(telegramGateway, never()).deliver(any(), any());
    }
}
