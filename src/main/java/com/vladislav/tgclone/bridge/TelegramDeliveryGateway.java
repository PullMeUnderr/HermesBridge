package com.vladislav.tgclone.bridge;

import com.vladislav.tgclone.conversation.ConversationMessage;
import com.vladislav.tgclone.conversation.ConversationAttachment;
import com.vladislav.tgclone.media.MediaStorageService;
import java.util.List;
import com.vladislav.tgclone.telegram.TelegramBotClient;
import org.springframework.stereotype.Component;

@Component
public class TelegramDeliveryGateway implements DeliveryGateway {

    private final TelegramBotClient telegramBotClient;
    private final MediaStorageService mediaStorageService;

    public TelegramDeliveryGateway(TelegramBotClient telegramBotClient, MediaStorageService mediaStorageService) {
        this.telegramBotClient = telegramBotClient;
        this.mediaStorageService = mediaStorageService;
    }

    @Override
    public BridgeTransport transport() {
        return BridgeTransport.TELEGRAM;
    }

    @Override
    public String deliver(TransportBinding binding, ConversationMessage message) {
        List<com.vladislav.tgclone.conversation.ConversationAttachment> attachments = message.getAttachments();
        if (attachments.isEmpty()) {
            String text = message.getAuthorDisplayName() + ": " + message.getBody();
            return telegramBotClient.sendMessage(binding.getExternalChatId(), text);
        }

        String firstMessageId = "";
        String caption = buildCaption(message);
        for (int index = 0; index < attachments.size(); index++) {
            ConversationAttachment attachment = attachments.get(index);
            String currentCaption = index == 0 ? caption : null;
            String messageId = deliverAttachment(binding, attachment, currentCaption);

            if (firstMessageId.isBlank() && messageId != null && !messageId.isBlank()) {
                firstMessageId = messageId;
            }
        }

        return firstMessageId;
    }

    private String buildCaption(ConversationMessage message) {
        String body = message.getBody() == null ? "" : message.getBody().trim();
        String caption = body.isBlank()
            ? message.getAuthorDisplayName()
            : message.getAuthorDisplayName() + ": " + body;
        return caption.length() > 1024 ? caption.substring(0, 1021) + "..." : caption;
    }

    private String deliverAttachment(TransportBinding binding, ConversationAttachment attachment, String caption) {
        java.nio.file.Path path = mediaStorageService.resolve(attachment.getStorageKey());
        if (attachment.getKind() == com.vladislav.tgclone.conversation.ConversationAttachmentKind.PHOTO
            && shouldSendAsPhoto(attachment)) {
            try {
                return telegramBotClient.sendPhoto(binding.getExternalChatId(), path, caption);
            } catch (Exception ignored) {
                // Some image formats are rejected by sendPhoto. Fall back to sendDocument instead of dropping the file.
            }
        }

        return telegramBotClient.sendDocument(binding.getExternalChatId(), path, caption);
    }

    private boolean shouldSendAsPhoto(ConversationAttachment attachment) {
        String mimeType = attachment.getMimeType();
        if (mimeType == null || mimeType.isBlank()) {
            return true;
        }

        String normalized = mimeType.trim().toLowerCase();
        return switch (normalized) {
            case "image/heic", "image/heif" -> false;
            default -> normalized.startsWith("image/");
        };
    }
}
