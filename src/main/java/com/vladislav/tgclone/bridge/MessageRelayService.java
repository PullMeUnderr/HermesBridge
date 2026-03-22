package com.vladislav.tgclone.bridge;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.common.NotFoundException;
import com.vladislav.tgclone.conversation.ConversationEventPublisher;
import com.vladislav.tgclone.conversation.ConversationMentionService;
import com.vladislav.tgclone.conversation.ConversationMessage;
import com.vladislav.tgclone.conversation.ConversationMessageRepository;
import com.vladislav.tgclone.conversation.ConversationService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MessageRelayService {

    private final TransportBindingRepository transportBindingRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final ConversationService conversationService;
    private final ConversationEventPublisher conversationEventPublisher;
    private final ConversationMentionService conversationMentionService;
    private final UserAccountService userAccountService;
    private final AsyncMessageFanOutService asyncMessageFanOutService;

    public MessageRelayService(
        TransportBindingRepository transportBindingRepository,
        ConversationMessageRepository conversationMessageRepository,
        DeliveryRecordRepository deliveryRecordRepository,
        ConversationService conversationService,
        ConversationEventPublisher conversationEventPublisher,
        ConversationMentionService conversationMentionService,
        UserAccountService userAccountService,
        AsyncMessageFanOutService asyncMessageFanOutService
    ) {
        this.transportBindingRepository = transportBindingRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.deliveryRecordRepository = deliveryRecordRepository;
        this.conversationService = conversationService;
        this.conversationEventPublisher = conversationEventPublisher;
        this.conversationMentionService = conversationMentionService;
        this.userAccountService = userAccountService;
        this.asyncMessageFanOutService = asyncMessageFanOutService;
    }

    public void relayInternalMessage(ConversationMessage message) {
        conversationEventPublisher.publish(message);
        asyncMessageFanOutService.fanOut(message.getId(), null);
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
        ConversationMessage replyToMessage = resolveTelegramReplyTarget(sourceBinding, envelope.replyToExternalMessageId());
        ConversationMessage savedMessage = conversationService.createExternalMessage(
            sourceBinding.getConversation().getId(),
            BridgeTransport.TELEGRAM,
            envelope.externalChatId(),
            envelope.externalMessageId(),
            authorUser,
            envelope.authorId(),
            envelope.authorDisplayName(),
            conversationMentionService.normalizeTelegramMentions(
                sourceBinding.getConversation().getId(),
                envelope.body()
            ),
            envelope.createdAt(),
            replyToMessage,
            envelope.attachments()
        );

        conversationEventPublisher.publish(savedMessage);
        asyncMessageFanOutService.fanOut(savedMessage.getId(), sourceBinding.getId());
    }

    private ConversationMessage resolveTelegramReplyTarget(TransportBinding sourceBinding, String replyToExternalMessageId) {
        if (replyToExternalMessageId == null || replyToExternalMessageId.isBlank()) {
            return null;
        }

        return conversationMessageRepository.findBySourceTransportAndSourceChatIdAndSourceMessageId(
                BridgeTransport.TELEGRAM,
                sourceBinding.getExternalChatId(),
                replyToExternalMessageId
            )
            .or(() -> deliveryRecordRepository.findByTargetTransportAndTargetChatIdAndTargetMessageId(
                    BridgeTransport.TELEGRAM,
                    sourceBinding.getExternalChatId(),
                    replyToExternalMessageId
                )
                .map(DeliveryRecord::getConversationMessage))
            .orElse(null);
    }
}
