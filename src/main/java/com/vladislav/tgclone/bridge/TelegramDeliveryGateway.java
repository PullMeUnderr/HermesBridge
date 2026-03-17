package com.vladislav.tgclone.bridge;

import com.vladislav.tgclone.conversation.ConversationMessage;
import com.vladislav.tgclone.conversation.ConversationAttachment;
import com.vladislav.tgclone.conversation.ConversationAttachmentKind;
import com.vladislav.tgclone.conversation.ConversationMentionService;
import com.vladislav.tgclone.media.MediaStorageService;
import java.util.List;
import com.vladislav.tgclone.telegram.TelegramBotClient;
import org.springframework.stereotype.Component;

@Component
public class TelegramDeliveryGateway implements DeliveryGateway {

    private final TelegramBotClient telegramBotClient;
    private final MediaStorageService mediaStorageService;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final ConversationMentionService conversationMentionService;

    public TelegramDeliveryGateway(
        TelegramBotClient telegramBotClient,
        MediaStorageService mediaStorageService,
        DeliveryRecordRepository deliveryRecordRepository,
        ConversationMentionService conversationMentionService
    ) {
        this.telegramBotClient = telegramBotClient;
        this.mediaStorageService = mediaStorageService;
        this.deliveryRecordRepository = deliveryRecordRepository;
        this.conversationMentionService = conversationMentionService;
    }

    @Override
    public BridgeTransport transport() {
        return BridgeTransport.TELEGRAM;
    }

    @Override
    public String deliver(TransportBinding binding, ConversationMessage message) {
        List<com.vladislav.tgclone.conversation.ConversationAttachment> attachments = message.getAttachments();
        String replyToMessageId = resolveReplyTargetMessageId(binding, message);
        if (attachments.isEmpty()) {
            return telegramBotClient.sendMessage(
                binding.getExternalChatId(),
                buildMessageText(message),
                null,
                replyToMessageId
            );
        }

        if (attachments.size() > 1 && canSendAsMediaGroup(attachments)) {
            try {
                return telegramBotClient.sendMediaGroup(
                    binding.getExternalChatId(),
                    attachments.stream().map(this::toMediaGroupItem).toList(),
                    buildCaption(message),
                    replyToMessageId
                );
            } catch (Exception ignored) {
                // Fall back to individual sends.
            }
        }

        String firstMessageId = "";
        String caption = buildCaption(message);
        for (int index = 0; index < attachments.size(); index++) {
            ConversationAttachment attachment = attachments.get(index);
            String currentCaption = index == 0 ? caption : null;
            String currentReplyToMessageId = index == 0 ? replyToMessageId : null;
            String messageId = deliverAttachment(binding, attachment, currentCaption, currentReplyToMessageId);

            if (firstMessageId.isBlank() && messageId != null && !messageId.isBlank()) {
                firstMessageId = messageId;
            }
        }

        return firstMessageId;
    }

    private String buildMessageText(ConversationMessage message) {
        String normalizedBody = conversationMentionService.resolveTelegramOutboundMentions(
            message.getConversation().getId(),
            message.getBody()
        );
        return message.getAuthorDisplayName() + ": " + normalizedBody;
    }

    private String buildCaption(ConversationMessage message) {
        String body = conversationMentionService.resolveTelegramOutboundMentions(
            message.getConversation().getId(),
            message.getBody()
        );
        body = body == null ? "" : body.trim();
        String caption = body.isBlank()
            ? message.getAuthorDisplayName()
            : message.getAuthorDisplayName() + ": " + body;
        return caption.length() > 1024 ? caption.substring(0, 1021) + "..." : caption;
    }

    private String deliverAttachment(
        TransportBinding binding,
        ConversationAttachment attachment,
        String caption,
        String replyToMessageId
    ) {
        java.nio.file.Path path = mediaStorageService.resolve(attachment.getStorageKey());
        return switch (attachment.getKind()) {
            case PHOTO -> deliverPhoto(binding, attachment, path, caption, replyToMessageId);
            case VIDEO -> deliverVideo(binding, attachment, path, caption, replyToMessageId);
            case VIDEO_NOTE -> deliverVideoNote(binding, attachment, path, caption, replyToMessageId);
            case VOICE -> deliverVoice(binding, attachment, path, caption, replyToMessageId);
            case DOCUMENT -> telegramBotClient.sendDocument(binding.getExternalChatId(), path, caption, replyToMessageId);
        };
    }

    private String deliverPhoto(
        TransportBinding binding,
        ConversationAttachment attachment,
        java.nio.file.Path path,
        String caption,
        String replyToMessageId
    ) {
        if (shouldSendAsPhoto(attachment)) {
            try {
                return telegramBotClient.sendPhoto(binding.getExternalChatId(), path, caption, replyToMessageId);
            } catch (Exception ignored) {
                // Fall through to document.
            }
        }
        return telegramBotClient.sendDocument(binding.getExternalChatId(), path, caption, replyToMessageId);
    }

    private String deliverVideo(
        TransportBinding binding,
        ConversationAttachment attachment,
        java.nio.file.Path path,
        String caption,
        String replyToMessageId
    ) {
        if (shouldSendAsVideo(attachment)) {
            try {
                return telegramBotClient.sendVideo(binding.getExternalChatId(), path, caption, replyToMessageId);
            } catch (Exception ignored) {
                // Fall through to document.
            }
        }
        return telegramBotClient.sendDocument(binding.getExternalChatId(), path, caption, replyToMessageId);
    }

    private String deliverVideoNote(
        TransportBinding binding,
        ConversationAttachment attachment,
        java.nio.file.Path path,
        String caption,
        String replyToMessageId
    ) {
        if (shouldSendAsVideo(attachment)) {
            try {
                return telegramBotClient.sendVideoNote(binding.getExternalChatId(), path, replyToMessageId);
            } catch (Exception ignored) {
                // Fall through to regular video or document.
            }
            try {
                return telegramBotClient.sendVideo(binding.getExternalChatId(), path, caption, replyToMessageId);
            } catch (Exception ignored) {
                // Fall through to document.
            }
        }
        return telegramBotClient.sendDocument(binding.getExternalChatId(), path, caption, replyToMessageId);
    }

    private String deliverVoice(
        TransportBinding binding,
        ConversationAttachment attachment,
        java.nio.file.Path path,
        String caption,
        String replyToMessageId
    ) {
        if (shouldSendAsVoice(attachment)) {
            try {
                return telegramBotClient.sendVoice(binding.getExternalChatId(), path, caption, replyToMessageId);
            } catch (Exception ignored) {
                // Fall through to document.
            }
        }
        return telegramBotClient.sendDocument(binding.getExternalChatId(), path, caption, replyToMessageId);
    }

    private String resolveReplyTargetMessageId(TransportBinding binding, ConversationMessage message) {
        ConversationMessage replyToMessage = message.getReplyToMessage();
        if (replyToMessage == null) {
            return null;
        }

        if (replyToMessage.getSourceTransport() == BridgeTransport.TELEGRAM
            && binding.getExternalChatId().equals(replyToMessage.getSourceChatId())
            && replyToMessage.getSourceMessageId() != null
            && !replyToMessage.getSourceMessageId().isBlank()
            && !replyToMessage.getSourceMessageId().startsWith("album:")) {
            return replyToMessage.getSourceMessageId();
        }

        return deliveryRecordRepository.findByConversationMessage_IdAndTargetTransportAndTargetChatId(
                replyToMessage.getId(),
                BridgeTransport.TELEGRAM,
                binding.getExternalChatId()
            )
            .map(DeliveryRecord::getTargetMessageId)
            .orElse(null);
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

    private boolean canSendAsMediaGroup(List<ConversationAttachment> attachments) {
        return attachments.stream().allMatch(attachment -> switch (attachment.getKind()) {
            case PHOTO -> shouldSendAsPhoto(attachment);
            case VIDEO -> shouldSendAsVideo(attachment);
            default -> false;
        });
    }

    private TelegramBotClient.TelegramMediaGroupItem toMediaGroupItem(ConversationAttachment attachment) {
        java.nio.file.Path path = mediaStorageService.resolve(attachment.getStorageKey());
        String type = attachment.getKind() == ConversationAttachmentKind.PHOTO ? "photo" : "video";
        return new TelegramBotClient.TelegramMediaGroupItem(type, path);
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
