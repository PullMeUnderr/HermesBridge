package com.vladislav.tgclone.bridge;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.common.NotFoundException;
import com.vladislav.tgclone.conversation.ConversationEventPublisher;
import com.vladislav.tgclone.conversation.ConversationMessage;
import com.vladislav.tgclone.conversation.ConversationMessageRepository;
import com.vladislav.tgclone.conversation.ConversationService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MessageRelayService {

    private static final Logger log = LoggerFactory.getLogger(MessageRelayService.class);

    private final TransportBindingRepository transportBindingRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final ConversationService conversationService;
    private final ConversationEventPublisher conversationEventPublisher;
    private final UserAccountService userAccountService;
    private final Map<BridgeTransport, DeliveryGateway> deliveryGateways;
    private final Clock clock;

    public MessageRelayService(
        TransportBindingRepository transportBindingRepository,
        ConversationMessageRepository conversationMessageRepository,
        DeliveryRecordRepository deliveryRecordRepository,
        ConversationService conversationService,
        ConversationEventPublisher conversationEventPublisher,
        UserAccountService userAccountService,
        List<DeliveryGateway> deliveryGateways,
        Clock clock
    ) {
        this.transportBindingRepository = transportBindingRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.deliveryRecordRepository = deliveryRecordRepository;
        this.conversationService = conversationService;
        this.conversationEventPublisher = conversationEventPublisher;
        this.userAccountService = userAccountService;
        this.deliveryGateways = deliveryGateways.stream()
            .collect(Collectors.toMap(DeliveryGateway::transport, Function.identity()));
        this.clock = clock;
    }

    public void relayInternalMessage(ConversationMessage message) {
        conversationEventPublisher.publish(message);
        fanOutToExternalBindings(message, null);
    }

    public void processTelegramInbound(TelegramInboundEnvelope envelope) {
        TransportBinding sourceBinding = transportBindingRepository
            .findByTransportAndExternalChatIdAndActiveTrue(BridgeTransport.TELEGRAM, envelope.externalChatId())
            .orElseThrow(() -> new NotFoundException(
                "No active Telegram binding for chat %s".formatted(envelope.externalChatId())
            ));

        boolean alreadyProcessed = conversationMessageRepository
            .findBySourceTransportAndSourceChatIdAndSourceMessageId(
                BridgeTransport.TELEGRAM,
                envelope.externalChatId(),
                envelope.externalMessageId()
            )
            .isPresent();

        if (alreadyProcessed) {
            return;
        }

        UserAccount authorUser = userAccountService.findByTelegramUserId(envelope.authorId()).orElse(null);
        ConversationMessage savedMessage = conversationService.createExternalMessage(
            sourceBinding.getConversation().getId(),
            BridgeTransport.TELEGRAM,
            envelope.externalChatId(),
            envelope.externalMessageId(),
            authorUser,
            envelope.authorId(),
            envelope.authorDisplayName(),
            envelope.body(),
            envelope.createdAt()
        );

        conversationEventPublisher.publish(savedMessage);
        fanOutToExternalBindings(savedMessage, sourceBinding.getId());
    }

    private void fanOutToExternalBindings(ConversationMessage message, Long excludedBindingId) {
        List<TransportBinding> bindings = transportBindingRepository
            .findAllByConversation_IdAndActiveTrue(message.getConversation().getId());

        for (TransportBinding binding : bindings) {
            if (excludedBindingId != null && excludedBindingId.equals(binding.getId())) {
                continue;
            }

            if (deliveryRecordRepository.existsByConversationMessage_IdAndTargetTransportAndTargetChatId(
                message.getId(),
                binding.getTransport(),
                binding.getExternalChatId()
            )) {
                continue;
            }

            DeliveryGateway gateway = deliveryGateways.get(binding.getTransport());
            if (gateway == null) {
                log.warn("No delivery gateway configured for transport {}", binding.getTransport());
                continue;
            }

            try {
                String targetMessageId = gateway.deliver(binding, message);
                DeliveryRecord record = new DeliveryRecord(
                    message,
                    binding.getTransport(),
                    binding.getExternalChatId(),
                    targetMessageId,
                    clock.instant()
                );
                deliveryRecordRepository.save(record);
            } catch (Exception ex) {
                log.warn(
                    "Failed to deliver message {} to {}:{} - {}",
                    message.getId(),
                    binding.getTransport(),
                    binding.getExternalChatId(),
                    ex.getMessage()
                );
            }
        }
    }
}
