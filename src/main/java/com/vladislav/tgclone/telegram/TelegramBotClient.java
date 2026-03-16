package com.vladislav.tgclone.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TelegramBotClient {

    private final TelegramProperties telegramProperties;
    private final RestClient restClient;

    public TelegramBotClient(RestClient.Builder restClientBuilder, TelegramProperties telegramProperties) {
        this.telegramProperties = telegramProperties;
        String baseUrl = "https://api.telegram.org/bot" + telegramProperties.botToken();
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public List<TelegramUpdateDto> fetchUpdates(long offset) {
        ensureConfigured();
        TelegramApiResponse<List<TelegramUpdateDto>> response = restClient.post()
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
        TelegramApiResponse<TelegramMessageDto> response = restClient.post()
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
            restClient.post()
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

    private void ensureConfigured() {
        if (telegramProperties.botToken() == null || telegramProperties.botToken().isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN is not configured");
        }
    }

    private <T> T unwrap(TelegramApiResponse<T> response) {
        if (response == null || !response.ok()) {
            String description = response == null ? "Telegram returned empty response" : response.description();
            throw new IllegalStateException("Telegram API request failed: " + description);
        }
        return response.result();
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
    String text
) {

    Instant sentAt() {
        return date == null ? Instant.now() : Instant.ofEpochSecond(date);
    }
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
