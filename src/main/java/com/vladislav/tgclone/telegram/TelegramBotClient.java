package com.vladislav.tgclone.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladislav.tgclone.conversation.ConversationAttachmentDraft;
import com.vladislav.tgclone.conversation.ConversationAttachmentKind;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TelegramBotClient {

    private final TelegramProperties telegramProperties;
    private final RestClient botRestClient;
    private final RestClient fileRestClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TelegramBotClient(RestClient.Builder restClientBuilder, TelegramProperties telegramProperties) {
        this.telegramProperties = telegramProperties;
        this.botRestClient = restClientBuilder
            .baseUrl("https://api.telegram.org/bot" + telegramProperties.botToken())
            .build();
        this.fileRestClient = restClientBuilder
            .baseUrl("https://api.telegram.org/file/bot" + telegramProperties.botToken())
            .build();
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public List<TelegramUpdateDto> fetchUpdates(long offset) {
        ensureConfigured();
        TelegramApiResponse<List<TelegramUpdateDto>> response = botRestClient.post()
            .uri("/getUpdates")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new GetUpdatesRequest(offset, telegramProperties.pollTimeoutSeconds(), List.of("message", "callback_query")))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });

        return unwrap(response);
    }

    public String sendMessage(String chatId, String text) {
        return sendMessage(chatId, text, null);
    }

    public String sendMessage(String chatId, String text, Map<String, Object> replyMarkup) {
        ensureConfigured();
        TelegramApiResponse<TelegramMessageDto> response = botRestClient.post()
            .uri("/sendMessage")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SendMessageRequest(chatId, text, replyMarkup))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });

        TelegramMessageDto result = unwrap(response);
        return result.messageId() == null ? "" : result.messageId().toString();
    }

    public void answerCallbackQuery(String callbackQueryId) {
        ensureConfigured();
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            return;
        }

        try {
            botRestClient.post()
                .uri("/answerCallbackQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AnswerCallbackQueryRequest(callbackQueryId))
                .retrieve()
                .body(new ParameterizedTypeReference<TelegramApiResponse<Boolean>>() {
                });
        } catch (Exception ignored) {
            // Callback answers are best-effort. If Telegram expires the query, we should not stop polling.
        }
    }

    public String sendPhoto(String chatId, Path path, String caption) {
        return sendMultipartMedia("/sendPhoto", "photo", chatId, path, caption);
    }

    public String sendVideo(String chatId, Path path, String caption) {
        return sendMultipartMedia("/sendVideo", "video", chatId, path, caption);
    }

    public String sendVoice(String chatId, Path path, String caption) {
        return sendMultipartMedia("/sendVoice", "voice", chatId, path, caption);
    }

    public String sendVideoNote(String chatId, Path path) {
        return sendMultipartMedia("/sendVideoNote", "video_note", chatId, path, null);
    }

    public String sendDocument(String chatId, Path path, String caption) {
        return sendMultipartMedia("/sendDocument", "document", chatId, path, caption);
    }

    public String sendMediaGroup(String chatId, List<TelegramMediaGroupItem> items, String caption) {
        ensureConfigured();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Telegram media group items are required");
        }

        try {
            String boundary = "HermesBoundary" + System.nanoTime();
            List<MultipartTextPart> textParts = new ArrayList<>();
            List<MultipartFilePart> fileParts = new ArrayList<>();
            List<Map<String, Object>> media = new ArrayList<>();

            textParts.add(new MultipartTextPart("chat_id", chatId));
            for (int index = 0; index < items.size(); index++) {
                TelegramMediaGroupItem item = items.get(index);
                String attachName = "file" + index;
                Path path = item.path();
                String fileName = path.getFileName() == null ? "attachment.bin" : path.getFileName().toString();
                String contentType = URLConnection.guessContentTypeFromName(fileName);
                if (contentType == null || contentType.isBlank()) {
                    contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
                }

                Map<String, Object> mediaItem = new LinkedHashMap<>();
                mediaItem.put("type", item.type());
                mediaItem.put("media", "attach://" + attachName);
                if (index == 0 && caption != null && !caption.isBlank()) {
                    mediaItem.put("caption", caption);
                }
                media.add(mediaItem);
                fileParts.add(new MultipartFilePart(attachName, path, fileName, contentType));
            }
            textParts.add(new MultipartTextPart("media", objectMapper.writeValueAsString(media)));

            String responseBody = sendMultipartRequest("/sendMediaGroup", boundary, textParts, fileParts);
            TelegramApiResponse<List<TelegramMessageDto>> parsed = objectMapper.readValue(
                responseBody,
                new TypeReference<TelegramApiResponse<List<TelegramMessageDto>>>() {
                }
            );
            List<TelegramMessageDto> result = unwrap(parsed);
            return result == null || result.isEmpty() || result.getFirst().messageId() == null
                ? ""
                : result.getFirst().messageId().toString();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Telegram media group upload failed", ex);
        }
    }

    public ConversationAttachmentDraft downloadAttachment(
        String fileId,
        ConversationAttachmentKind kind,
        String originalFilename,
        String mimeType,
        Long sizeBytes
    ) {
        ensureConfigured();
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("Telegram fileId is required");
        }

        TelegramApiResponse<TelegramFileResultDto> response = botRestClient.post()
            .uri("/getFile")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("file_id", fileId))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });

        TelegramFileResultDto fileResult = unwrap(response);
        if (fileResult == null || fileResult.filePath() == null || fileResult.filePath().isBlank()) {
            throw new IllegalStateException("Telegram did not return file_path");
        }

        byte[] content = fileRestClient.get()
            .uri("/" + fileResult.filePath())
            .retrieve()
            .body(byte[].class);

        String resolvedName = resolveFilename(originalFilename, fileResult.filePath(), kind);
        String resolvedMimeType = resolveMimeType(mimeType, resolvedName, kind);
        long resolvedSizeBytes = sizeBytes != null && sizeBytes > 0
            ? sizeBytes
            : content == null ? 0 : content.length;

        return new ConversationAttachmentDraft(
            kind,
            resolvedName,
            resolvedMimeType,
            resolvedSizeBytes,
            content == null ? new byte[0] : content
        );
    }

    private void ensureConfigured() {
        if (telegramProperties.botToken() == null || telegramProperties.botToken().isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN is not configured");
        }
    }

    private String sendMultipartMedia(String endpoint, String partName, String chatId, Path path, String caption) {
        ensureConfigured();
        try {
            String fileName = path.getFileName() == null ? "attachment.bin" : path.getFileName().toString();
            String contentType = URLConnection.guessContentTypeFromName(fileName);
            if (contentType == null || contentType.isBlank()) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            String boundary = "HermesBoundary" + System.nanoTime();
            List<MultipartTextPart> textParts = new ArrayList<>();
            textParts.add(new MultipartTextPart("chat_id", chatId));
            if (caption != null && !caption.isBlank()) {
                textParts.add(new MultipartTextPart("caption", caption));
            }
            String responseBody = sendMultipartRequest(
                endpoint,
                boundary,
                textParts,
                List.of(new MultipartFilePart(partName, path, fileName, contentType))
            );
            TelegramApiResponse<TelegramMessageDto> parsed = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructParametricType(TelegramApiResponse.class, TelegramMessageDto.class)
            );
            TelegramMessageDto result = unwrap(parsed);
            return result.messageId() == null ? "" : result.messageId().toString();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Telegram media upload failed", ex);
        }
    }

    private <T> T unwrap(TelegramApiResponse<T> response) {
        if (response == null || !response.ok()) {
            String description = response == null ? "Telegram returned empty response" : response.description();
            throw new IllegalStateException("Telegram API request failed: " + description);
        }
        return response.result();
    }

    private String sendMultipartRequest(
        String endpoint,
        String boundary,
        List<MultipartTextPart> textParts,
        List<MultipartFilePart> fileParts
    ) throws IOException, InterruptedException {
        byte[] body = buildMultipartBody(boundary, textParts, fileParts);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.telegram.org/bot" + telegramProperties.botToken() + endpoint))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    private String resolveFilename(String originalFilename, String filePath, ConversationAttachmentKind kind) {
        if (originalFilename != null && !originalFilename.isBlank()) {
            return originalFilename.trim();
        }

        Path path = Path.of(filePath);
        Path fileName = path.getFileName();
        if (fileName != null && !fileName.toString().isBlank()) {
            return fileName.toString();
        }

        return switch (kind) {
            case PHOTO -> "telegram-photo.jpg";
            case VIDEO -> "telegram-video.mp4";
            case VIDEO_NOTE -> "telegram-video-note.mp4";
            case VOICE -> "telegram-voice.ogg";
            case DOCUMENT -> "telegram-document.bin";
        };
    }

    private String resolveMimeType(String mimeType, String fileName, ConversationAttachmentKind kind) {
        if (mimeType != null && !mimeType.isBlank()) {
            return mimeType.trim();
        }

        String guessed = URLConnection.guessContentTypeFromName(fileName);
        if (guessed != null && !guessed.isBlank()) {
            return guessed;
        }

        return switch (kind) {
            case PHOTO -> MediaType.IMAGE_JPEG_VALUE;
            case VIDEO -> "video/mp4";
            case VIDEO_NOTE -> "video/mp4";
            case VOICE -> "audio/ogg";
            case DOCUMENT -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    private byte[] buildMultipartBody(
        String boundary,
        List<MultipartTextPart> textParts,
        List<MultipartFilePart> fileParts
    ) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        for (MultipartTextPart textPart : textParts) {
            writeTextPart(outputStream, boundary, textPart.name(), textPart.value());
        }

        for (MultipartFilePart filePart : fileParts) {
            byte[] fileContent = Files.readAllBytes(filePart.path());
            outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write(
                ("Content-Disposition: form-data; name=\"" + filePart.name() + "\"; filename=\"" + escapeQuotes(filePart.fileName()) + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8)
            );
            outputStream.write(("Content-Type: " + filePart.contentType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write(fileContent);
            outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        outputStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return outputStream.toByteArray();
    }

    private void writeTextPart(ByteArrayOutputStream outputStream, String boundary, String name, String value) throws IOException {
        outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(
            ("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8)
        );
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String escapeQuotes(String value) {
        return value.replace("\"", "\\\"");
    }

    public record TelegramMediaGroupItem(
        String type,
        Path path
    ) {
    }

    private record MultipartTextPart(String name, String value) {
    }

    private record MultipartFilePart(String name, Path path, String fileName, String contentType) {
    }
}

record GetUpdatesRequest(
    long offset,
    @JsonProperty("timeout")
    int timeoutSeconds,
    @JsonProperty("allowed_updates")
    List<String> allowedUpdates
) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record SendMessageRequest(
    @JsonProperty("chat_id")
    String chatId,
    String text,
    @JsonProperty("reply_markup")
    Map<String, Object> replyMarkup
) {
}

record AnswerCallbackQueryRequest(
    @JsonProperty("callback_query_id")
    String callbackQueryId
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TelegramApiResponse<T>(
    boolean ok,
    T result,
    String description
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TelegramUpdateDto(
    @JsonProperty("update_id")
    Long updateId,
    TelegramMessageDto message,
    @JsonProperty("callback_query")
    TelegramCallbackQueryDto callbackQuery
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TelegramMessageDto(
    @JsonProperty("message_id")
    Long messageId,
    TelegramChatDto chat,
    TelegramUserDto from,
    Long date,
    String text,
    String caption,
    @JsonProperty("media_group_id")
    String mediaGroupId,
    List<TelegramPhotoSizeDto> photo,
    TelegramDocumentDto document,
    TelegramVideoDto video,
    @JsonProperty("video_note")
    TelegramVideoNoteDto videoNote,
    TelegramVoiceDto voice
) {

    Instant sentAt() {
        return date == null ? Instant.now() : Instant.ofEpochSecond(date);
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TelegramPhotoSizeDto(
    @JsonProperty("file_id")
    String fileId,
    @JsonProperty("file_unique_id")
    String fileUniqueId,
    Integer width,
    Integer height,
    @JsonProperty("file_size")
    Long fileSize
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TelegramDocumentDto(
    @JsonProperty("file_id")
    String fileId,
    @JsonProperty("file_unique_id")
    String fileUniqueId,
    @JsonProperty("file_name")
    String fileName,
    @JsonProperty("mime_type")
    String mimeType,
    @JsonProperty("file_size")
    Long fileSize
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TelegramVideoDto(
    @JsonProperty("file_id")
    String fileId,
    @JsonProperty("file_unique_id")
    String fileUniqueId,
    @JsonProperty("file_name")
    String fileName,
    @JsonProperty("mime_type")
    String mimeType,
    @JsonProperty("file_size")
    Long fileSize
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TelegramVoiceDto(
    @JsonProperty("file_id")
    String fileId,
    @JsonProperty("file_unique_id")
    String fileUniqueId,
    @JsonProperty("mime_type")
    String mimeType,
    @JsonProperty("file_size")
    Long fileSize
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TelegramVideoNoteDto(
    @JsonProperty("file_id")
    String fileId,
    @JsonProperty("file_unique_id")
    String fileUniqueId,
    @JsonProperty("file_size")
    Long fileSize
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TelegramChatDto(
    Long id,
    String title,
    String type
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TelegramUserDto(
    Long id,
    @JsonProperty("first_name")
    String firstName,
    @JsonProperty("last_name")
    String lastName,
    String username
) {

    String displayName() {
        StringBuilder displayName = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            displayName.append(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (displayName.length() > 0) {
                displayName.append(' ');
            }
            displayName.append(lastName.trim());
        }
        if (displayName.length() == 0 && username != null && !username.isBlank()) {
            displayName.append('@').append(username.trim());
        }
        return displayName.length() == 0 ? "Telegram User" : displayName.toString();
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TelegramCallbackQueryDto(
    String id,
    TelegramUserDto from,
    TelegramMessageDto message,
    String data
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record TelegramFileResultDto(
    @JsonProperty("file_id")
    String fileId,
    @JsonProperty("file_unique_id")
    String fileUniqueId,
    @JsonProperty("file_path")
    String filePath,
    @JsonProperty("file_size")
    Long fileSize
) {
}
