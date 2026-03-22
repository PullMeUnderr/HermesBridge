package com.vladislav.tgclone.bridge;

import com.vladislav.tgclone.conversation.ConversationMessage;
import com.vladislav.tgclone.conversation.ConversationMessageRepository;
import com.vladislav.tgclone.conversation.ConversationEventPublisher;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncMessageFanOutService {

    private static final Logger log = LoggerFactory.getLogger(AsyncMessageFanOutService.class);

    private final TransportBindingRepository transportBindingRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final Map<BridgeTransport, DeliveryGateway> deliveryGateways;
    private final ConversationEventPublisher conversationEventPublisher;
    private final Clock clock;

    public AsyncMessageFanOutService(
        TransportBindingRepository transportBindingRepository,
        ConversationMessageRepository conversationMessageRepository,
        DeliveryRecordRepository deliveryRecordRepository,
        List<DeliveryGateway> deliveryGateways,
        ConversationEventPublisher conversationEventPublisher,
        Clock clock
    ) {
        this.transportBindingRepository = transportBindingRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.deliveryRecordRepository = deliveryRecordRepository;
        this.deliveryGateways = deliveryGateways.stream()
            .collect(Collectors.toMap(DeliveryGateway::transport, Function.identity()));
        this.conversationEventPublisher = conversationEventPublisher;
        this.clock = clock;
    }

    @Async("messageRelayExecutor")
    @Transactional
    public void fanOut(Long messageId, Long excludedBindingId) {
        if (messageId == null) {
            return;
        }

        ConversationMessage message = conversationMessageRepository.findById(messageId).orElse(null);
        if (message == null) {
            return;
        }

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
