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
        return switch (attachment.getKind()) {
            case PHOTO -> deliverPhoto(binding, attachment, path, caption);
            case VIDEO -> deliverVideo(binding, attachment, path, caption);
            case VOICE -> deliverVoice(binding, attachment, path, caption);
            case DOCUMENT -> telegramBotClient.sendDocument(binding.getExternalChatId(), path, caption);
        };
    }

    private String deliverPhoto(TransportBinding binding, ConversationAttachment attachment, java.nio.file.Path path, String caption) {
        if (shouldSendAsPhoto(attachment)) {
            try {
                return telegramBotClient.sendPhoto(binding.getExternalChatId(), path, caption);
            } catch (Exception ignored) {
                // Fall through to document.
            }
        }
        return telegramBotClient.sendDocument(binding.getExternalChatId(), path, caption);
    }

    private String deliverVideo(TransportBinding binding, ConversationAttachment attachment, java.nio.file.Path path, String caption) {
        if (shouldSendAsVideo(attachment)) {
            try {
                return telegramBotClient.sendVideo(binding.getExternalChatId(), path, caption);
            } catch (Exception ignored) {
                // Fall through to document.
            }
        }
        return telegramBotClient.sendDocument(binding.getExternalChatId(), path, caption);
    }

    private String deliverVoice(TransportBinding binding, ConversationAttachment attachment, java.nio.file.Path path, String caption) {
        if (shouldSendAsVoice(attachment)) {
            try {
                return telegramBotClient.sendVoice(binding.getExternalChatId(), path, caption);
            } catch (Exception ignored) {
                // Fall through to document.
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

    private boolean shouldSendAsVideo(ConversationAttachment attachment) {
        String mimeType = attachment.getMimeType();
        if (mimeType != null && !mimeType.isBlank() && mimeType.trim().toLowerCase().startsWith("video/")) {
            return true;
        }

        String fileName = attachment.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            return false;
        }

        String normalizedName = fileName.trim().toLowerCase();
        return normalizedName.endsWith(".mp4")
            || normalizedName.endsWith(".mov")
            || normalizedName.endsWith(".m4v")
            || normalizedName.endsWith(".webm")
            || normalizedName.endsWith(".mkv")
            || normalizedName.endsWith(".avi");
    }

    private boolean shouldSendAsVoice(ConversationAttachment attachment) {
        String mimeType = attachment.getMimeType();
        if (mimeType != null && !mimeType.isBlank()) {
            String normalized = mimeType.trim().toLowerCase();
            if (normalized.startsWith("audio/ogg")
                || normalized.equals("audio/mpeg")
                || normalized.equals("audio/mp3")
                || normalized.equals("audio/mp4")
                || normalized.equals("audio/x-m4a")
                || normalized.equals("audio/aac")
                || normalized.equals("audio/webm")
                || normalized.equals("audio/x-webm")) {
                return true;
            }
        }

        String fileName = attachment.getOriginalFilename();
        if (fileName == null) {
            return false;
        }

        String normalizedName = fileName.trim().toLowerCase();
        return normalizedName.endsWith(".ogg")
            || normalizedName.endsWith(".oga")
            || normalizedName.endsWith(".opus")
            || normalizedName.endsWith(".mp3")
            || normalizedName.endsWith(".m4a")
            || normalizedName.endsWith(".webm");
    }
}
