package com.vladislav.tgclone;

import com.vladislav.tgclone.account.AccountProperties;
import com.vladislav.tgclone.conversation.ConversationProperties;
import com.vladislav.tgclone.media.MediaProperties;
import com.vladislav.tgclone.telegram.TelegramProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({TelegramProperties.class, AccountProperties.class, ConversationProperties.class, MediaProperties.class})
public class TgCloneApplication {

    public static void main(String[] args) {
        SpringApplication.run(TgCloneApplication.class, args);
    }
}
