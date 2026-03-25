package com.vladislav.tgclone.tdlight;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.account.UserAccountService;
import com.vladislav.tgclone.security.AuthenticatedUser;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionDescriptor;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionService;
import com.vladislav.tgclone.tdlight.migration.ChannelMigrationRequest;
import com.vladislav.tgclone.tdlight.migration.ChannelMigrationService;
import com.vladislav.tgclone.tdlight.migration.ChannelMigrationSummary;
import com.vladislav.tgclone.tdlight.migration.TdlightAvailableChannelSummary;
import com.vladislav.tgclone.tdlight.migration.TdlightChannelSubscriptionRequest;
import com.vladislav.tgclone.tdlight.migration.TdlightChannelSubscriptionService;
import com.vladislav.tgclone.tdlight.migration.TdlightChannelSubscriptionSummary;
import com.vladislav.tgclone.tdlight.migration.TdlightIngestionCoordinator;
import com.vladislav.tgclone.tdlight.migration.TdlightDiagnosticsService;
import com.vladislav.tgclone.tdlight.migration.TdlightPublicChannelResolveService;
import com.vladislav.tgclone.tdlight.migration.TdlightQrAuthorizationService;
import com.vladislav.tgclone.tdlight.migration.TdlightRuntimeReadinessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/tdlight")
public class TdlightDevController {

    private static final Logger log = LoggerFactory.getLogger(TdlightDevController.class);

    private final UserAccountService userAccountService;
    private final TdlightConnectionService tdlightConnectionService;
    private final ChannelMigrationService channelMigrationService;
    private final TdlightChannelSubscriptionService tdlightChannelSubscriptionService;
    private final TdlightIngestionCoordinator tdlightIngestionCoordinator;
    private final TdlightDiagnosticsService tdlightDiagnosticsService;
    private final TdlightPublicChannelResolveService tdlightPublicChannelResolveService;
    private final TdlightRuntimeReadinessService tdlightRuntimeReadinessService;
    private final TdlightQrAuthorizationService tdlightQrAuthorizationService;

    public TdlightDevController(
        UserAccountService userAccountService,
        TdlightConnectionService tdlightConnectionService,
        ChannelMigrationService channelMigrationService,
        TdlightChannelSubscriptionService tdlightChannelSubscriptionService,
        ObjectProvider<TdlightIngestionCoordinator> tdlightIngestionCoordinator,
        TdlightDiagnosticsService tdlightDiagnosticsService,
        TdlightPublicChannelResolveService tdlightPublicChannelResolveService,
        TdlightRuntimeReadinessService tdlightRuntimeReadinessService,
        ObjectProvider<TdlightQrAuthorizationService> tdlightQrAuthorizationService
    ) {
        this.userAccountService = userAccountService;
        this.tdlightConnectionService = tdlightConnectionService;
        this.channelMigrationService = channelMigrationService;
        this.tdlightChannelSubscriptionService = tdlightChannelSubscriptionService;
        this.tdlightIngestionCoordinator = tdlightIngestionCoordinator.getIfAvailable();
        this.tdlightDiagnosticsService = tdlightDiagnosticsService;
        this.tdlightPublicChannelResolveService = tdlightPublicChannelResolveService;
        this.tdlightRuntimeReadinessService = tdlightRuntimeReadinessService;
        this.tdlightQrAuthorizationService = tdlightQrAuthorizationService.getIfAvailable();
    }

    @GetMapping("/connections")
    public List<TdlightConnectionResponse> listConnections(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return tdlightConnectionService.listConnections(requireUser(authenticatedUser)).stream()
            .map(TdlightConnectionResponse::from)
            .toList();
    }

    @PostMapping("/connections/dev")
    public ResponseEntity<TdlightConnectionResponse> createDevelopmentConnection(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody CreateDevTdlightConnectionRequest request
    ) {
        TdlightConnectionDescriptor connection = tdlightConnectionService.createDevelopmentConnection(
            requireUser(authenticatedUser),
            request.phoneMask(),
            request.tdlightUserId(),
            request.forceNew()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(TdlightConnectionResponse.from(connection));
    }

    @GetMapping("/channels")
    public List<TdlightAvailableChannelResponse> listAvailableChannels(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @org.springframework.web.bind.annotation.RequestParam("tdlightConnectionId") Long tdlightConnectionId
    ) {
        UserAccount userAccount = requireUser(authenticatedUser);
        List<TdlightAvailableChannelResponse> response = tdlightChannelSubscriptionService.listAvailableChannels(
            userAccount,
            tdlightConnectionId
        ).stream().map(TdlightAvailableChannelResponse::from).toList();
        log.info(
            "TDLight channels resolved userId={} username={} tdlightConnectionId={} count={}",
            userAccount.getId(),
            userAccount.getUsername(),
            tdlightConnectionId,
            response.size()
        );
        return response;
    }

    @GetMapping("/subscriptions")
    public List<TdlightChannelSubscriptionResponse> listSubscriptions(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return tdlightChannelSubscriptionService.listSubscriptions(requireUser(authenticatedUser)).stream()
            .map(TdlightChannelSubscriptionResponse::from)
            .toList();
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<TdlightChannelSubscriptionResponse> subscribeToChannel(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody CreateTdlightChannelSubscriptionRequest request
    ) {
        TdlightChannelSubscriptionSummary summary = tdlightChannelSubscriptionService.subscribe(
            requireUser(authenticatedUser),
            new TdlightChannelSubscriptionRequest(
                request.tdlightConnectionId(),
                request.telegramChannelId(),
                request.telegramChannelHandle(),
                request.channelTitle(),
                request.avatarUrl()
            )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(TdlightChannelSubscriptionResponse.from(summary));
    }

    @DeleteMapping("/subscriptions/conversation/{conversationId}")
    public ResponseEntity<Void> disconnectChannelByConversation(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable("conversationId") Long conversationId
    ) {
        tdlightChannelSubscriptionService.disconnectByConversation(requireUser(authenticatedUser), conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/migrations")
    public List<ChannelMigrationResponse> listMigrations(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return channelMigrationService.listRecent(requireUser(authenticatedUser)).stream()
            .map(ChannelMigrationResponse::from)
            .toList();
    }

    @GetMapping("/migrations/{migrationId}")
    public ChannelMigrationResponse getMigration(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable("migrationId") Long migrationId
    ) {
        return channelMigrationService.findById(requireUser(authenticatedUser), migrationId)
            .map(ChannelMigrationResponse::from)
            .orElseThrow(() -> new IllegalArgumentException("Migration not found"));
    }

    @PostMapping("/migrations")
    public ResponseEntity<ChannelMigrationResponse> queueMigration(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody QueuePublicChannelMigrationRequest request
    ) {
        ChannelMigrationSummary summary = channelMigrationService.queuePublicChannelMigration(
            requireUser(authenticatedUser),
            new ChannelMigrationRequest(
                null,
                request.tdlightConnectionId(),
                request.telegramChannelId(),
                request.telegramChannelHandle(),
                true,
                request.importMedia(),
                false
            )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ChannelMigrationResponse.from(summary));
    }

    @PostMapping("/migrations/{migrationId}/process")
    public ChannelMigrationResponse processMigrationNow(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable("migrationId") Long migrationId
    ) {
        UserAccount userAccount = requireUser(authenticatedUser);
        channelMigrationService.findById(userAccount, migrationId)
            .orElseThrow(() -> new IllegalArgumentException("Migration not found"));
        requireTdlightIngestionCoordinator().processMigrationNow(migrationId);
        return channelMigrationService.findById(userAccount, migrationId)
            .map(ChannelMigrationResponse::from)
            .orElseThrow(() -> new IllegalArgumentException("Migration not found"));
    }

    @PostMapping("/cleanup")
    public ResponseEntity<Void> cleanupImportedContent(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        requireUser(authenticatedUser);
        requireTdlightIngestionCoordinator().cleanupNow();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/diagnostics/public-channel")
    public TdlightPublicChannelDiagnosticsResponse inspectPublicChannel(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody TdlightPublicChannelDiagnosticsRequest request
    ) {
        return TdlightPublicChannelDiagnosticsResponse.from(
            tdlightDiagnosticsService.inspectPublicChannel(
                requireUser(authenticatedUser),
                request.tdlightConnectionId(),
                request.telegramChannelId(),
                request.telegramChannelHandle()
            )
        );
    }

    @PostMapping("/resolve/public-channel")
    public TdlightResolvedPublicChannelResponse resolvePublicChannel(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody TdlightResolvePublicChannelRequest request
    ) {
        return TdlightResolvedPublicChannelResponse.from(
            tdlightPublicChannelResolveService.resolvePublicChannel(
                requireUser(authenticatedUser),
                request.tdlightConnectionId(),
                request.telegramChannelId(),
                request.telegramChannelHandle()
            )
        );
    }

    @GetMapping("/readiness")
    public TdlightRuntimeReadinessResponse readiness(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        requireUser(authenticatedUser);
        return TdlightRuntimeReadinessResponse.from(tdlightRuntimeReadinessService.inspect());
    }

    @PostMapping("/auth/qr/start")
    public TdlightQrAuthorizationStatusResponse startQrAuthorization(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody TdlightConnectionRequest request
    ) {
        return TdlightQrAuthorizationStatusResponse.from(
            requireQrAuthorizationService().startQrAuthorization(
                requireUser(authenticatedUser),
                request.tdlightConnectionId()
            )
        );
    }

    @PostMapping("/auth/code/start")
    public TdlightQrAuthorizationStatusResponse startCodeAuthorization(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody TdlightCodeAuthorizationStartRequest request
    ) {
        return TdlightQrAuthorizationStatusResponse.from(
            requireQrAuthorizationService().startCodeAuthorization(
                requireUser(authenticatedUser),
                request.tdlightConnectionId(),
                request.phoneNumber()
            )
        );
    }

    @PostMapping("/auth/code/submit")
    public TdlightQrAuthorizationStatusResponse submitCode(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody TdlightCodeSubmitRequest request
    ) {
        return TdlightQrAuthorizationStatusResponse.from(
            requireQrAuthorizationService().submitCode(
                requireUser(authenticatedUser),
                request.tdlightConnectionId(),
                request.code()
            )
        );
    }

    @PostMapping("/auth/password/submit")
    public TdlightQrAuthorizationStatusResponse submitPassword(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody TdlightPasswordSubmitRequest request
    ) {
        return TdlightQrAuthorizationStatusResponse.from(
            requireQrAuthorizationService().submitPassword(
                requireUser(authenticatedUser),
                request.tdlightConnectionId(),
                request.password()
            )
        );
    }

    @PostMapping("/auth/reset")
    public TdlightQrAuthorizationStatusResponse resetAuthorization(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody TdlightConnectionRequest request
    ) {
        return TdlightQrAuthorizationStatusResponse.from(
            requireQrAuthorizationService().resetAuthorization(
                requireUser(authenticatedUser),
                request.tdlightConnectionId()
            )
        );
    }

    @GetMapping("/auth/{tdlightConnectionId}/status")
    public TdlightQrAuthorizationStatusResponse qrAuthorizationStatus(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable("tdlightConnectionId") Long tdlightConnectionId
    ) {
        return TdlightQrAuthorizationStatusResponse.from(
            requireQrAuthorizationService().getStatus(
                requireUser(authenticatedUser),
                tdlightConnectionId
            )
        );
    }

    private TdlightQrAuthorizationService requireQrAuthorizationService() {
        if (tdlightQrAuthorizationService == null) {
            throw new IllegalStateException("TDLight QR authorization is available only in REAL mode");
        }
        return tdlightQrAuthorizationService;
    }

    private TdlightIngestionCoordinator requireTdlightIngestionCoordinator() {
        if (tdlightIngestionCoordinator == null) {
            throw new IllegalStateException("TDLight ingestion coordinator is not available");
        }
        return tdlightIngestionCoordinator;
    }

    private UserAccount requireUser(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.userId() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return userAccountService.requireActiveUser(authenticatedUser.userId());
    }
}

record CreateDevTdlightConnectionRequest(
    String phoneMask,
    String tdlightUserId,
    boolean forceNew
) {
}

record QueuePublicChannelMigrationRequest(
    @NotNull(message = "tdlightConnectionId is required")
    Long tdlightConnectionId,
    @NotBlank(message = "telegramChannelId is required")
    String telegramChannelId,
    String telegramChannelHandle,
    boolean importMedia
) {
}

record TdlightPublicChannelDiagnosticsRequest(
    @NotNull(message = "tdlightConnectionId is required")
    Long tdlightConnectionId,
    @NotBlank(message = "telegramChannelId is required")
    String telegramChannelId,
    String telegramChannelHandle
) {
}

record TdlightCodeAuthorizationStartRequest(
    @NotNull(message = "tdlightConnectionId is required")
    Long tdlightConnectionId,
    @NotBlank(message = "phoneNumber is required")
    String phoneNumber
) {
}

record TdlightCodeSubmitRequest(
    @NotNull(message = "tdlightConnectionId is required")
    Long tdlightConnectionId,
    @NotBlank(message = "code is required")
    String code
) {
}

record TdlightPasswordSubmitRequest(
    @NotNull(message = "tdlightConnectionId is required")
    Long tdlightConnectionId,
    @NotBlank(message = "password is required")
    String password
) {
}

record TdlightResolvePublicChannelRequest(
    @NotNull(message = "tdlightConnectionId is required")
    Long tdlightConnectionId,
    @NotBlank(message = "telegramChannelId is required")
    String telegramChannelId,
    String telegramChannelHandle
) {
}

record TdlightConnectionRequest(
    @NotNull(message = "tdlightConnectionId is required")
    Long tdlightConnectionId
) {
}

record CreateTdlightChannelSubscriptionRequest(
    @NotNull(message = "tdlightConnectionId is required")
    Long tdlightConnectionId,
    @NotBlank(message = "telegramChannelId is required")
    String telegramChannelId,
    String telegramChannelHandle,
    String channelTitle,
    String avatarUrl
) {
}

record TdlightConnectionResponse(
    Long id,
    Long userAccountId,
    String status,
    String phoneMask,
    String tdlightUserId,
    Instant createdAt,
    Instant lastVerifiedAt
) {

    static TdlightConnectionResponse from(TdlightConnectionDescriptor descriptor) {
        return new TdlightConnectionResponse(
            descriptor.id(),
            descriptor.userAccountId(),
            descriptor.status().name(),
            descriptor.phoneMask(),
            descriptor.tdlightUserId(),
            descriptor.createdAt(),
            descriptor.lastVerifiedAt()
        );
    }
}

record TdlightAvailableChannelResponse(
    String telegramChannelId,
    String telegramChannelHandle,
    String channelTitle,
    String avatarUrl,
    boolean subscribed,
    Long subscriptionId,
    Long conversationId
) {

    static TdlightAvailableChannelResponse from(TdlightAvailableChannelSummary summary) {
        return new TdlightAvailableChannelResponse(
            summary.telegramChannelId(),
            summary.telegramChannelHandle(),
            summary.channelTitle(),
            summary.avatarUrl(),
            summary.subscribed(),
            summary.subscriptionId(),
            summary.conversationId()
        );
    }
}

record TdlightChannelSubscriptionResponse(
    Long id,
    Long tdlightConnectionId,
    Long conversationId,
    String telegramChannelId,
    String telegramChannelHandle,
    String channelTitle,
    String status,
    Instant subscribedAt,
    String lastSyncedRemoteMessageId,
    Instant lastSyncedAt,
    String lastError,
    Instant createdAt,
    Instant updatedAt
) {

    static TdlightChannelSubscriptionResponse from(TdlightChannelSubscriptionSummary summary) {
        return new TdlightChannelSubscriptionResponse(
            summary.id(),
            summary.tdlightConnectionId(),
            summary.conversationId(),
            summary.telegramChannelId(),
            summary.telegramChannelHandle(),
            summary.channelTitle(),
            summary.status().name(),
            summary.subscribedAt(),
            summary.lastSyncedRemoteMessageId(),
            summary.lastSyncedAt(),
            summary.lastError(),
            summary.createdAt(),
            summary.updatedAt()
        );
    }
}

record ChannelMigrationResponse(
    Long id,
    Long initiatedByUserId,
    Long tdlightConnectionId,
    Long targetConversationId,
    String telegramChannelId,
    String telegramChannelHandle,
    String status,
    int importedMessageCount,
    int importedMediaCount,
    String lastError,
    Instant createdAt,
    Instant updatedAt
) {

    static ChannelMigrationResponse from(ChannelMigrationSummary summary) {
        return new ChannelMigrationResponse(
            summary.id(),
            summary.initiatedByUserId(),
            summary.tdlightConnectionId(),
            summary.targetConversationId(),
            summary.telegramChannelId(),
            summary.telegramChannelHandle(),
            summary.status().name(),
            summary.importedMessageCount(),
            summary.importedMediaCount(),
            summary.lastError(),
            summary.createdAt(),
            summary.updatedAt()
        );
    }
}

record TdlightPublicChannelDiagnosticsResponse(
    Long tdlightConnectionId,
    String telegramChannelId,
    String telegramChannelHandle,
    String channelTitle,
    int messageLimit,
    long maxImportedMediaBytes,
    int maxImportedVideoDurationSeconds,
    int rawFetchedPostCount,
    int mappedPostCount,
    List<TdlightPublicChannelDiagnosticsPostResponse> posts
) {

    static TdlightPublicChannelDiagnosticsResponse from(
        TdlightDiagnosticsService.TdlightPublicChannelDiagnostics diagnostics
    ) {
        return new TdlightPublicChannelDiagnosticsResponse(
            diagnostics.tdlightConnectionId(),
            diagnostics.sourceChannelId(),
            diagnostics.sourceChannelHandle(),
            diagnostics.channelTitle(),
            diagnostics.messageLimit(),
            diagnostics.maxImportedMediaBytes(),
            diagnostics.maxImportedVideoDurationSeconds(),
            diagnostics.rawFetchedPostCount(),
            diagnostics.mappedPostCount(),
            diagnostics.posts().stream()
                .map(TdlightPublicChannelDiagnosticsPostResponse::from)
                .toList()
        );
    }
}

record TdlightPublicChannelDiagnosticsPostResponse(
    String remoteMessageId,
    String authorDisplayName,
    Instant publishedAt,
    int rawMediaCount,
    int importedMediaCount,
    int skippedMediaCount,
    List<TdlightPublicChannelDiagnosticsMediaDecisionResponse> mediaDecisions
) {

    static TdlightPublicChannelDiagnosticsPostResponse from(
        TdlightDiagnosticsService.TdlightDiagnosticsPost post
    ) {
        return new TdlightPublicChannelDiagnosticsPostResponse(
            post.remoteMessageId(),
            post.authorDisplayName(),
            post.publishedAt(),
            post.rawMediaCount(),
            post.importedMediaCount(),
            post.skippedMediaCount(),
            post.mediaDecisions().stream()
                .map(TdlightPublicChannelDiagnosticsMediaDecisionResponse::from)
                .toList()
        );
    }
}

record TdlightPublicChannelDiagnosticsMediaDecisionResponse(
    String fileName,
    String mimeType,
    long sizeBytes,
    int durationSeconds,
    boolean imported,
    String reason
) {

    static TdlightPublicChannelDiagnosticsMediaDecisionResponse from(
        TdlightDiagnosticsService.TdlightDiagnosticsMediaDecision decision
    ) {
        return new TdlightPublicChannelDiagnosticsMediaDecisionResponse(
            decision.fileName(),
            decision.mimeType(),
            decision.sizeBytes(),
            decision.durationSeconds(),
            decision.imported(),
            decision.reason()
        );
    }
}

record TdlightResolvedPublicChannelResponse(
    String originalReference,
    String telegramChannelId,
    String telegramChannelHandle,
    String channelTitle,
    String normalizedReference,
    String referenceKind,
    boolean publicChannel,
    String eligibility,
    String eligibilityReason,
    boolean eligibleForMigration
) {

    static TdlightResolvedPublicChannelResponse from(
        TdlightPublicChannelResolveService.ResolvedPublicChannel resolvedPublicChannel
    ) {
        return new TdlightResolvedPublicChannelResponse(
            resolvedPublicChannel.originalReference(),
            resolvedPublicChannel.sourceChannelId(),
            resolvedPublicChannel.sourceChannelHandle(),
            resolvedPublicChannel.title(),
            resolvedPublicChannel.normalizedReference(),
            resolvedPublicChannel.referenceKind(),
            resolvedPublicChannel.publicChannel(),
            resolvedPublicChannel.eligibility(),
            resolvedPublicChannel.eligibilityReason(),
            resolvedPublicChannel.eligibleForMigration()
        );
    }
}

record TdlightRuntimeReadinessResponse(
    boolean ready,
    String clientMode,
    boolean tdlightClassesPresent,
    List<String> blockers,
    List<String> hints
) {

    static TdlightRuntimeReadinessResponse from(
        TdlightRuntimeReadinessService.TdlightRuntimeReadiness readiness
    ) {
        return new TdlightRuntimeReadinessResponse(
            readiness.ready(),
            readiness.clientMode(),
            readiness.tdlightClassesPresent(),
            readiness.blockers(),
            readiness.hints()
        );
    }
}

record TdlightQrAuthorizationStatusResponse(
    Long tdlightConnectionId,
    String phase,
    String qrLink,
    String phoneNumber,
    String lastError,
    Instant updatedAt
) {

    static TdlightQrAuthorizationStatusResponse from(
        TdlightQrAuthorizationService.QrAuthorizationStatus status
    ) {
        return new TdlightQrAuthorizationStatusResponse(
            status.tdlightConnectionId(),
            status.phase(),
            status.qrLink(),
            status.phoneNumber(),
            status.lastError(),
            status.updatedAt()
        );
    }
}
