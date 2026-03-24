package com.vladislav.tgclone.config.tdlight;

import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.entitlement.EntitlementService;
import com.vladislav.tgclone.media.MediaStorageService;
import com.vladislav.tgclone.tdlight.TdlightDevController;
import com.vladislav.tgclone.tdlight.TdlightLocalQrPageController;
import com.vladislav.tgclone.tdlight.TdlightProperties;
import com.vladislav.tgclone.tdlight.condition.ConditionalOnTdlightStubMode;
import com.vladislav.tgclone.tdlight.connection.DefaultTdlightConnectionService;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionRepository;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionService;
import com.vladislav.tgclone.tdlight.migration.ChannelMigrationRepository;
import com.vladislav.tgclone.tdlight.migration.ChannelMigrationService;
import com.vladislav.tgclone.tdlight.migration.DefaultTdlightIngestionCoordinator;
import com.vladislav.tgclone.tdlight.migration.DefaultChannelMigrationService;
import com.vladislav.tgclone.tdlight.migration.DefaultTdlightPublicChannelGateway;
import com.vladislav.tgclone.tdlight.migration.TdlightChannelReader;
import com.vladislav.tgclone.tdlight.migration.TdlightChannelSnapshotMapper;
import com.vladislav.tgclone.tdlight.migration.TdlightDiagnosticsService;
import com.vladislav.tgclone.tdlight.migration.TdlightIngestionCoordinator;
import com.vladislav.tgclone.tdlight.migration.TdlightGatewayChannelReader;
import com.vladislav.tgclone.tdlight.migration.TdlightMediaImportPlanner;
import com.vladislav.tgclone.tdlight.migration.TdlightNativeLibraryBootstrap;
import com.vladislav.tgclone.tdlight.migration.TdlightPublicChannelClient;
import com.vladislav.tgclone.tdlight.migration.TdlightPublicChannelGateway;
import com.vladislav.tgclone.tdlight.migration.TdlightPublicChannelReferenceParser;
import com.vladislav.tgclone.tdlight.migration.TdlightPublicChannelResolveService;
import com.vladislav.tgclone.tdlight.migration.TdlightQrAuthorizationService;
import com.vladislav.tgclone.tdlight.migration.TdlightRuntimeAdapter;
import com.vladislav.tgclone.tdlight.migration.TdlightRuntimeReadinessService;
import com.vladislav.tgclone.tdlight.migration.TdlightSessionFactory;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.tdlight", name = "enabled", havingValue = "true")
@Import({
    TdlightSharedConfiguration.class,
    TdlightStubConfiguration.class,
    TdlightRealConfiguration.class
})
public class TdlightModuleConfiguration {
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.tdlight", name = "enabled", havingValue = "true")
class TdlightSharedConfiguration {

    @Bean
    TdlightConnectionService tdlightConnectionService(
        TdlightConnectionRepository tdlightConnectionRepository,
        TdlightProperties tdlightProperties,
        Clock clock
    ) {
        return new DefaultTdlightConnectionService(tdlightConnectionRepository, tdlightProperties, clock);
    }

    @Bean
    TdlightPublicChannelReferenceParser tdlightPublicChannelReferenceParser() {
        return new TdlightPublicChannelReferenceParser();
    }

    @Bean
    TdlightMediaImportPlanner tdlightMediaImportPlanner() {
        return new TdlightMediaImportPlanner();
    }

    @Bean
    ChannelMigrationService channelMigrationService(
        ChannelMigrationRepository channelMigrationRepository,
        TdlightConnectionRepository tdlightConnectionRepository,
        EntitlementService entitlementService,
        TdlightPublicChannelReferenceParser tdlightPublicChannelReferenceParser,
        TdlightPublicChannelResolveService tdlightPublicChannelResolveService,
        TdlightProperties tdlightProperties,
        Clock clock
    ) {
        return new DefaultChannelMigrationService(
            channelMigrationRepository,
            tdlightConnectionRepository,
            entitlementService,
            tdlightPublicChannelReferenceParser,
            tdlightPublicChannelResolveService,
            tdlightProperties,
            clock
        );
    }

    @Bean
    TdlightPublicChannelGateway tdlightPublicChannelGateway(TdlightPublicChannelClient tdlightPublicChannelClient) {
        return new DefaultTdlightPublicChannelGateway(tdlightPublicChannelClient);
    }

    @Bean
    TdlightChannelSnapshotMapper tdlightChannelSnapshotMapper() {
        return new TdlightChannelSnapshotMapper();
    }

    @Bean
    TdlightChannelReader tdlightChannelReader(
        TdlightPublicChannelGateway tdlightPublicChannelGateway,
        TdlightChannelSnapshotMapper tdlightChannelSnapshotMapper
    ) {
        return new TdlightGatewayChannelReader(tdlightPublicChannelGateway, tdlightChannelSnapshotMapper);
    }

    @Bean
    TdlightPublicChannelResolveService tdlightPublicChannelResolveService(
        TdlightConnectionRepository tdlightConnectionRepository,
        TdlightPublicChannelClient tdlightPublicChannelClient,
        TdlightPublicChannelReferenceParser tdlightPublicChannelReferenceParser,
        TdlightProperties tdlightProperties
    ) {
        return new TdlightPublicChannelResolveService(
            tdlightConnectionRepository,
            tdlightPublicChannelClient,
            tdlightPublicChannelReferenceParser,
            tdlightProperties
        );
    }

    @Bean
    TdlightRuntimeReadinessService tdlightRuntimeReadinessService(
        TdlightProperties tdlightProperties,
        ObjectProvider<TdlightRuntimeAdapter> tdlightRuntimeAdapter,
        ObjectProvider<TdlightSessionFactory> tdlightSessionFactory,
        ObjectProvider<TdlightNativeLibraryBootstrap> tdlightNativeLibraryBootstrap
    ) {
        return new TdlightRuntimeReadinessService(
            tdlightProperties,
            tdlightRuntimeAdapter.getIfAvailable(),
            tdlightSessionFactory.getIfAvailable(),
            tdlightNativeLibraryBootstrap.getIfAvailable()
        );
    }

    @Bean
    TdlightDiagnosticsService tdlightDiagnosticsService(
        TdlightConnectionRepository tdlightConnectionRepository,
        TdlightPublicChannelGateway tdlightPublicChannelGateway,
        TdlightChannelSnapshotMapper tdlightChannelSnapshotMapper,
        TdlightMediaImportPlanner tdlightMediaImportPlanner,
        TdlightPublicChannelReferenceParser tdlightPublicChannelReferenceParser,
        TdlightProperties tdlightProperties,
        Clock clock
    ) {
        return new TdlightDiagnosticsService(
            tdlightConnectionRepository,
            tdlightPublicChannelGateway,
            tdlightChannelSnapshotMapper,
            tdlightMediaImportPlanner,
            tdlightPublicChannelReferenceParser,
            tdlightProperties,
            clock
        );
    }

    @Bean
    TdlightIngestionCoordinator tdlightIngestionCoordinator(
        ChannelMigrationRepository channelMigrationRepository,
        com.vladislav.tgclone.conversation.ConversationRepository conversationRepository,
        com.vladislav.tgclone.conversation.ConversationService conversationService,
        com.vladislav.tgclone.conversation.ConversationMessageRepository conversationMessageRepository,
        com.vladislav.tgclone.conversation.ConversationAttachmentRepository conversationAttachmentRepository,
        com.vladislav.tgclone.conversation.ConversationMemberRepository conversationMemberRepository,
        com.vladislav.tgclone.conversation.ConversationInviteRepository conversationInviteRepository,
        com.vladislav.tgclone.bridge.TransportBindingRepository transportBindingRepository,
        TdlightProperties tdlightProperties,
        TdlightChannelReader tdlightChannelReader,
        TdlightMediaImportPlanner tdlightMediaImportPlanner,
        MediaStorageService mediaStorageService,
        Clock clock
    ) {
        return new DefaultTdlightIngestionCoordinator(
            channelMigrationRepository,
            conversationRepository,
            conversationService,
            conversationMessageRepository,
            conversationAttachmentRepository,
            conversationMemberRepository,
            conversationInviteRepository,
            transportBindingRepository,
            tdlightProperties,
            tdlightChannelReader,
            tdlightMediaImportPlanner,
            mediaStorageService,
            clock
        );
    }

    @Bean
    @Profile("local")
    TdlightDevController tdlightDevController(
        UserAccountService userAccountService,
        TdlightConnectionService tdlightConnectionService,
        ChannelMigrationService channelMigrationService,
        ObjectProvider<TdlightIngestionCoordinator> tdlightIngestionCoordinator,
        TdlightDiagnosticsService tdlightDiagnosticsService,
        TdlightPublicChannelResolveService tdlightPublicChannelResolveService,
        TdlightRuntimeReadinessService tdlightRuntimeReadinessService,
        ObjectProvider<TdlightQrAuthorizationService> tdlightQrAuthorizationService
    ) {
        return new TdlightDevController(
            userAccountService,
            tdlightConnectionService,
            channelMigrationService,
            tdlightIngestionCoordinator,
            tdlightDiagnosticsService,
            tdlightPublicChannelResolveService,
            tdlightRuntimeReadinessService,
            tdlightQrAuthorizationService
        );
    }

    @Bean
    @Profile("local")
    TdlightLocalQrPageController tdlightLocalQrPageController(
        @org.springframework.beans.factory.annotation.Value("${app.auth.master-token:}") String masterToken
    ) {
        return new TdlightLocalQrPageController(masterToken);
    }
}

@Configuration(proxyBeanMethods = false)
@Profile("local")
@ConditionalOnTdlightStubMode
class TdlightStubConfiguration {

    @Bean
    TdlightPublicChannelClient tdlightPublicChannelClient(Clock clock) {
        return new com.vladislav.tgclone.tdlight.migration.StubTdlightPublicChannelClient(clock);
    }
}
