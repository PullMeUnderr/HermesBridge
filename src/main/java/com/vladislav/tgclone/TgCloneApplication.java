package com.vladislav.tgclone;

import com.vladislav.tgclone.account.AccountProperties;
import com.vladislav.tgclone.bridge.MessageRelayService;
import com.vladislav.tgclone.config.AppConfig;
import com.vladislav.tgclone.config.StompAuthenticationChannelInterceptor;
import com.vladislav.tgclone.config.WebSocketConfig;
import com.vladislav.tgclone.config.tdlight.TdlightModuleConfiguration;
import com.vladislav.tgclone.conversation.ConversationProperties;
import com.vladislav.tgclone.entitlement.EntitlementService;
import com.vladislav.tgclone.media.MediaProperties;
import com.vladislav.tgclone.security.AuthenticatedUser;
import com.vladislav.tgclone.telegram.TelegramBotClient;
import com.vladislav.tgclone.telegram.TelegramProperties;
import com.vladislav.tgclone.tdlight.TdlightProperties;
import com.vladislav.tgclone.tdlight.connection.TdlightAccountBindingService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackageClasses = {
        AccountProperties.class,
        MessageRelayService.class,
        ConversationProperties.class,
        EntitlementService.class,
        MediaProperties.class,
        TelegramBotClient.class,
        TdlightAccountBindingService.class,
        AuthenticatedUser.class
    }
)
@EnableScheduling
@Import({
    AppConfig.class,
    StompAuthenticationChannelInterceptor.class,
    WebSocketConfig.class,
    TdlightModuleConfiguration.class
})
@EnableConfigurationProperties({
    TelegramProperties.class,
    AccountProperties.class,
    ConversationProperties.class,
    MediaProperties.class,
    TdlightProperties.class
})
public class TgCloneApplication {

    public static void main(String[] args) {
        SpringApplication.run(TgCloneApplication.class, args);
    }
}
