package com.vladislav.tgclone.telegram;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TelegramBotClientSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendMessageRequestOmitsReplyMarkupWhenNull() throws Exception {
        String json = objectMapper.writeValueAsString(new SendMessageRequest("123", "hello", null));

        assertFalse(json.contains("reply_markup"));
    }

    @Test
    void sendMessageRequestIncludesReplyMarkupObjectWhenPresent() throws Exception {
        String json = objectMapper.writeValueAsString(new SendMessageRequest(
            "123",
            "hello",
            Map.of("inline_keyboard", java.util.List.of())
        ));

        assertTrue(json.contains("\"reply_markup\""));
        assertTrue(json.contains("\"inline_keyboard\""));
    }
}
