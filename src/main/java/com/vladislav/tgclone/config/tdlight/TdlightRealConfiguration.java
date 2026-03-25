package com.vladislav.tgclone.config.tdlight;

import com.vladislav.tgclone.tdlight.TdlightProperties;
import com.vladislav.tgclone.tdlight.condition.ConditionalOnTdlightRealMode;
import com.vladislav.tgclone.tdlight.migration.ClasspathTdlightNativeLibraryBootstrap;
import com.vladislav.tgclone.tdlight.migration.DefaultTdlightSessionFactory;
import com.vladislav.tgclone.tdlight.migration.SdkTdlightRuntimeAdapter;
import com.vladislav.tgclone.tdlight.migration.InMemoryTdlightSessionSecretStore;
import com.vladislav.tgclone.tdlight.migration.InMemoryTdlightSessionStateStore;
import com.vladislav.tgclone.tdlight.migration.JpaTdlightSessionSecretStore;
import com.vladislav.tgclone.tdlight.migration.JpaTdlightSessionStateStore;
import com.vladislav.tgclone.tdlight.migration.RealTdlightPublicChannelClient;
import com.vladislav.tgclone.tdlight.migration.TdlightNativeLibraryBootstrap;
import com.vladislav.tgclone.tdlight.migration.TdlightQrAuthorizationService;
import com.vladislav.tgclone.tdlight.migration.TdlightRuntimeAdapter;
import com.vladislav.tgclone.tdlight.migration.TdlightSessionBindingRepository;
import com.vladislav.tgclone.tdlight.migration.TdlightSessionFactory;
import com.vladislav.tgclone.tdlight.migration.TdlightSessionSecretRepository;
import com.vladislav.tgclone.tdlight.migration.TdlightSessionSecretStore;
import com.vladislav.tgclone.tdlight.migration.TdlightSessionStateStore;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionRepository;
import com.vladislav.tgclone.tdlight.connection.TdlightAccountBindingService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@ConditionalOnTdlightRealMode
class TdlightRealConfiguration {

    @Bean
    @Primary
    TdlightSessionStateStore tdlightSessionStateStore(
        Clock clock,
        TdlightSessionBindingRepository tdlightSessionBindingRepository
    ) {
        return new JpaTdlightSessionStateStore(clock, tdlightSessionBindingRepository);
    }

    @Bean
    @ConditionalOnMissingBean(TdlightSessionStateStore.class)
    TdlightSessionStateStore inMemoryTdlightSessionStateStore(Clock clock) {
        return new InMemoryTdlightSessionStateStore(clock);
    }

    @Bean
    @Primary
    TdlightSessionSecretStore tdlightSessionSecretStore(
        Clock clock,
        TdlightSessionBindingRepository tdlightSessionBindingRepository,
        TdlightSessionSecretRepository tdlightSessionSecretRepository
    ) {
        return new JpaTdlightSessionSecretStore(clock, tdlightSessionBindingRepository, tdlightSessionSecretRepository);
    }

    @Bean
    @ConditionalOnMissingBean(TdlightSessionSecretStore.class)
    TdlightSessionSecretStore inMemoryTdlightSessionSecretStore(Clock clock) {
        return new InMemoryTdlightSessionSecretStore(clock);
    }

    @Bean
    TdlightNativeLibraryBootstrap tdlightNativeLibraryBootstrap() {
        return new ClasspathTdlightNativeLibraryBootstrap();
    }

    @Bean
    TdlightSessionFactory tdlightSessionFactory(
        TdlightSessionStateStore tdlightSessionStateStore,
        TdlightSessionSecretStore tdlightSessionSecretStore
    ) {
        return new DefaultTdlightSessionFactory(tdlightSessionStateStore, tdlightSessionSecretStore);
    }

    @Bean
    TdlightRuntimeAdapter tdlightRuntimeAdapter(
        TdlightNativeLibraryBootstrap tdlightNativeLibraryBootstrap,
        TdlightSessionFactory tdlightSessionFactory,
        TdlightAccountBindingService tdlightAccountBindingService
    ) {
        return new SdkTdlightRuntimeAdapter(tdlightNativeLibraryBootstrap, tdlightSessionFactory, tdlightAccountBindingService);
    }

    @Bean
    com.vladislav.tgclone.tdlight.migration.TdlightPublicChannelClient tdlightPublicChannelClient(
        TdlightProperties tdlightProperties,
        TdlightRuntimeAdapter tdlightRuntimeAdapter
    ) {
        return new RealTdlightPublicChannelClient(tdlightProperties, tdlightRuntimeAdapter);
    }

    @Bean
    TdlightQrAuthorizationService tdlightQrAuthorizationService(
        TdlightConnectionRepository tdlightConnectionRepository,
        TdlightAccountBindingService tdlightAccountBindingService,
        TdlightNativeLibraryBootstrap tdlightNativeLibraryBootstrap,
        TdlightSessionStateStore tdlightSessionStateStore,
        TdlightSessionSecretStore tdlightSessionSecretStore,
        TdlightProperties tdlightProperties,
        Clock clock
    ) {
        return new TdlightQrAuthorizationService(
            tdlightConnectionRepository,
            tdlightAccountBindingService,
            tdlightNativeLibraryBootstrap,
            tdlightSessionStateStore,
            tdlightSessionSecretStore,
            tdlightProperties,
            clock
        );
    }
}
