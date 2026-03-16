package com.vladislav.tgclone.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
