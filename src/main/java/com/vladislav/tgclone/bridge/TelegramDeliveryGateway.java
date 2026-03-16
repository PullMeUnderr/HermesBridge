package com.vladislav.tgclone.bridge;

import com.vladislav.tgclone.conversation.ConversationMessage;
import com.vladislav.tgclone.telegram.TelegramBotClient;
import org.springframework.stereotype.Component;

@Component
public class TelegramDeliveryGateway implements DeliveryGateway {

    private final TelegramBotClient telegramBotClient;

    public TelegramDeliveryGateway(TelegramBotClient telegramBotClient) {
        this.telegramBotClient = telegramBotClient;
    }

    @Override
    public BridgeTransport transport() {
        return BridgeTransport.TELEGRAM;
    }

    @Override
    public String deliver(TransportBinding binding, ConversationMessage message) {
        String text = message.getAuthorDisplayName() + ": " + message.getBody();
        return telegramBotClient.sendMessage(binding.getExternalChatId(), text);
    }
}
