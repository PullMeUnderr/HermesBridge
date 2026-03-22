package com.vladislav.tgclone.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vladislav.tgclone.account.TelegramRegistrationResult;
import com.vladislav.tgclone.account.TelegramRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TelegramRegistrationService telegramRegistrationService;

    @Test
    void registerAndLoginWithHermesCredentialsWorks() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "alice",
                      "displayName": "Alice",
                      "password": "password123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("tgclone_refresh_token"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.username").value("alice"))
            .andExpect(jsonPath("$.user.passwordLinked").value(true))
            .andExpect(jsonPath("$.user.telegramLinked").value(false));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "alice",
                      "password": "password123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("tgclone_refresh_token"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.username").value("alice"));
    }

    @Test
    void telegramLinkStartRequiresAuthenticatedSession() throws Exception {
        mockMvc.perform(post("/api/auth/link/telegram/start"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void telegramLinkStartReturnsShortLivedCodeForAuthenticatedUser() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "bob",
                      "displayName": "Bob",
                      "password": "password123"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String accessToken = JsonTestUtils.readJson(registerResult.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/auth/link/telegram/start")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("tgc_link_")))
            .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void publicMeEndpointStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void telegramFirstUserCanCompleteHermesRegistrationOnSameAccount() throws Exception {
        TelegramRegistrationResult telegramRegistration = telegramRegistrationService.registerOrRefresh(
            "501",
            "tg_alice",
            "Telegram Alice",
            "9001"
        );

        MvcResult sessionResult = mockMvc.perform(post("/api/auth/session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "%s"
                    }
                    """.formatted(telegramRegistration.plainTextToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.id").value(telegramRegistration.userId()))
            .andExpect(jsonPath("$.user.telegramLinked").value(true))
            .andExpect(jsonPath("$.user.passwordLinked").value(false))
            .andReturn();

        String accessToken = JsonTestUtils.readJson(sessionResult.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/auth/me/complete-registration")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "alice_web",
                      "displayName": "Alice Web",
                      "password": "password123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(telegramRegistration.userId()))
            .andExpect(jsonPath("$.username").value("alice_web"))
            .andExpect(jsonPath("$.displayName").value("Alice Web"))
            .andExpect(jsonPath("$.telegramLinked").value(true))
            .andExpect(jsonPath("$.passwordLinked").value(true));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "alice_web",
                      "password": "password123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.id").value(telegramRegistration.userId()))
            .andExpect(jsonPath("$.user.passwordLinked").value(true))
            .andExpect(jsonPath("$.user.telegramLinked").value(true));
    }
}
