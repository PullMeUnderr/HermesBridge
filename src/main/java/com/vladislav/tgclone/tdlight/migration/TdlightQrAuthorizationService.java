package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.tdlight.TdlightClientMode;
import com.vladislav.tgclone.tdlight.TdlightProperties;
import com.vladislav.tgclone.tdlight.TdlightRuntimeConfiguration;
import com.vladislav.tgclone.tdlight.condition.ConditionalOnTdlightRealMode;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import com.vladislav.tgclone.tdlight.connection.TdlightAccountBindingService;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionRepository;
import com.vladislav.tgclone.tdlight.connection.TdlightConnectionStatus;
import it.tdlight.client.APIToken;
import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.client.ClientInteraction;
import it.tdlight.client.InputParameter;
import it.tdlight.client.ParameterInfo;
import it.tdlight.client.ParameterInfoCode;
import it.tdlight.client.ParameterInfoNotifyLink;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientBuilder;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import it.tdlight.jni.TdApi;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnTdlightRealMode
public class TdlightQrAuthorizationService {

    private final TdlightConnectionRepository tdlightConnectionRepository;
    private final TdlightAccountBindingService tdlightAccountBindingService;
    private final TdlightNativeLibraryBootstrap tdlightNativeLibraryBootstrap;
    private final TdlightProperties tdlightProperties;
    private final Clock clock;
    private final Map<Long, ActiveQrAuthorizationSession> sessions = new ConcurrentHashMap<>();

    public TdlightQrAuthorizationService(
        TdlightConnectionRepository tdlightConnectionRepository,
        TdlightAccountBindingService tdlightAccountBindingService,
        TdlightNativeLibraryBootstrap tdlightNativeLibraryBootstrap,
        TdlightProperties tdlightProperties,
        Clock clock
    ) {
        this.tdlightConnectionRepository = tdlightConnectionRepository;
        this.tdlightAccountBindingService = tdlightAccountBindingService;
        this.tdlightNativeLibraryBootstrap = tdlightNativeLibraryBootstrap;
        this.tdlightProperties = tdlightProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public QrAuthorizationStatus startQrAuthorization(UserAccount initiatedBy, Long tdlightConnectionId) {
        if (!tdlightProperties.enabled() || !tdlightProperties.migrationEnabled()) {
            throw new IllegalStateException("TDLight QR authorization is disabled");
        }
        if (tdlightProperties.publicChannelClientMode() != TdlightClientMode.REAL) {
            throw new IllegalStateException("TDLight QR authorization requires REAL mode");
        }
        TdlightConnection connection = requireActiveConnection(initiatedBy, tdlightConnectionId);
        closeExistingSession(connection.getId());

        QrAuthorizationState state = new QrAuthorizationState(connection.getId(), QrAuthorizationPhase.STARTING, null, null, null, clock.instant());
        ActiveQrAuthorizationSession session = createSession(connection, state, AuthenticationSupplier.qrCode());
        sessions.put(connection.getId(), session);
        return toStatus(connection.getId(), session.state().current());
    }

    @Transactional(readOnly = true)
    public QrAuthorizationStatus startCodeAuthorization(
        UserAccount initiatedBy,
        Long tdlightConnectionId,
        String phoneNumber
    ) {
        if (!tdlightProperties.enabled() || !tdlightProperties.migrationEnabled()) {
            throw new IllegalStateException("TDLight code authorization is disabled");
        }
        if (tdlightProperties.publicChannelClientMode() != TdlightClientMode.REAL) {
            throw new IllegalStateException("TDLight code authorization requires REAL mode");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phoneNumber is required");
        }
        TdlightConnection connection = requireActiveConnection(initiatedBy, tdlightConnectionId);
        closeExistingSession(connection.getId());

        QrAuthorizationState state = new QrAuthorizationState(connection.getId(), QrAuthorizationPhase.STARTING, null, phoneNumber.trim(), null, clock.instant());
        ActiveQrAuthorizationSession session = createSession(
            connection,
            state,
            AuthenticationSupplier.user(phoneNumber.trim())
        );
        sessions.put(connection.getId(), session);
        return toStatus(connection.getId(), session.state().current());
    }

    @Transactional(readOnly = true)
    public QrAuthorizationStatus getStatus(UserAccount initiatedBy, Long tdlightConnectionId) {
        TdlightConnection connection = requireActiveConnection(initiatedBy, tdlightConnectionId);
        ActiveQrAuthorizationSession session = sessions.get(connection.getId());
        if (session == null) {
            return toStatus(connection.getId(), new QrAuthorizationState(connection.getId(), QrAuthorizationPhase.NOT_STARTED, null, null, null, null));
        }
        return toStatus(connection.getId(), session.state().current());
    }

    @Transactional(readOnly = true)
    public QrAuthorizationStatus submitCode(UserAccount initiatedBy, Long tdlightConnectionId, String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        TdlightConnection connection = requireActiveConnection(initiatedBy, tdlightConnectionId);
        ActiveQrAuthorizationSession session = requireSession(connection.getId());
        if (!session.state().current().phase().equals(QrAuthorizationPhase.WAIT_CODE)) {
            throw new IllegalStateException("TDLight session is not waiting for a code");
        }
        CompletableFuture<String> pendingCode = session.pendingCode();
        if (pendingCode == null || pendingCode.isDone()) {
            throw new IllegalStateException("TDLight session has no pending code request");
        }
        pendingCode.complete(code.trim());
        session.clearPendingCode();
        session.state().update(QrAuthorizationPhase.STARTING, session.state().current().qrLink(), session.state().current().phoneNumber(), null, clock.instant());
        return toStatus(connection.getId(), session.state().current());
    }

    @Transactional(readOnly = true)
    public QrAuthorizationStatus submitPassword(UserAccount initiatedBy, Long tdlightConnectionId, String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        TdlightConnection connection = requireActiveConnection(initiatedBy, tdlightConnectionId);
        ActiveQrAuthorizationSession session = requireSession(connection.getId());
        if (!session.state().current().phase().equals(QrAuthorizationPhase.WAIT_PASSWORD)) {
            throw new IllegalStateException("TDLight session is not waiting for a password");
        }
        CompletableFuture<String> pendingPassword = session.pendingPassword();
        if (pendingPassword == null || pendingPassword.isDone()) {
            throw new IllegalStateException("TDLight session has no pending password request");
        }
        pendingPassword.complete(password);
        session.clearPendingPassword();
        session.state().update(QrAuthorizationPhase.STARTING, session.state().current().qrLink(), session.state().current().phoneNumber(), null, clock.instant());
        return toStatus(connection.getId(), session.state().current());
    }

    private ActiveQrAuthorizationSession createSession(
        TdlightConnection connection,
        QrAuthorizationState initialState,
        AuthenticationSupplier<?> authenticationSupplier
    ) {
        TdlightRuntimeConfiguration runtimeConfiguration = new TdlightRuntimeConfiguration(
            blankToNull(tdlightProperties.libraryPath()),
            blankToNull(tdlightProperties.nativeWorkdir()),
            requireText(tdlightProperties.databaseDirectory(), "app.tdlight.database-directory"),
            requireText(tdlightProperties.filesDirectory(), "app.tdlight.files-directory"),
            Objects.requireNonNull(tdlightProperties.apiId(), "app.tdlight.api-id is required"),
            requireText(tdlightProperties.apiHash(), "app.tdlight.api-hash"),
            tdlightProperties.systemLanguageCode(),
            tdlightProperties.deviceModel(),
            tdlightProperties.applicationVersion()
        );
        tdlightNativeLibraryBootstrap.ensureLoaded(runtimeConfiguration);

        TDLibSettings settings = TDLibSettings.create(new APIToken(runtimeConfiguration.apiId(), runtimeConfiguration.apiHash()));
        settings.setDatabaseDirectoryPath(Path.of(runtimeConfiguration.databaseDirectory()));
        settings.setDownloadedFilesDirectoryPath(Path.of(runtimeConfiguration.filesDirectory()));
        settings.setUseTestDatacenter(false);
        settings.setSystemLanguageCode(runtimeConfiguration.systemLanguageCode());
        settings.setDeviceModel(runtimeConfiguration.deviceModel());
        settings.setApplicationVersion(runtimeConfiguration.applicationVersion());

        QrAuthorizationStateHolder holder = new QrAuthorizationStateHolder(initialState);
        SimpleTelegramClientFactory factory = new SimpleTelegramClientFactory();
        ActiveQrAuthorizationSession session = new ActiveQrAuthorizationSession(factory, null, holder);
        SimpleTelegramClientBuilder builder = factory.builder(settings);
        builder.setClientInteraction(new QrClientInteraction(session, clock));
        builder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, update -> handleAuthorizationUpdate(session, update));
        SimpleTelegramClient client = builder.build(authenticationSupplier);
        session.setClient(client);
        return session;
    }

    private void handleAuthorizationUpdate(
        ActiveQrAuthorizationSession session,
        TdApi.UpdateAuthorizationState update
    ) {
        if (update == null || update.authorizationState == null) {
            return;
        }
        QrAuthorizationStateHolder holder = session.state();
        TdApi.AuthorizationState authorizationState = update.authorizationState;
        if (authorizationState instanceof TdApi.AuthorizationStateReady) {
            syncAuthorizedConnection(session, holder.current());
            holder.update(QrAuthorizationPhase.READY, holder.current().qrLink(), null, null, clock.instant());
            closeInteractiveResources(session);
            return;
        }
        if (authorizationState instanceof TdApi.AuthorizationStateWaitCode waitCode) {
            String phoneNumber = waitCode.codeInfo == null ? null : waitCode.codeInfo.phoneNumber;
            holder.update(QrAuthorizationPhase.WAIT_CODE, holder.current().qrLink(), phoneNumber, null, clock.instant());
            return;
        }
        if (authorizationState instanceof TdApi.AuthorizationStateWaitPassword) {
            holder.update(QrAuthorizationPhase.WAIT_PASSWORD, holder.current().qrLink(), null, null, clock.instant());
        }
    }

    private TdlightConnection requireActiveConnection(UserAccount initiatedBy, Long tdlightConnectionId) {
        if (initiatedBy == null || initiatedBy.getId() == null) {
            throw new IllegalArgumentException("initiatedBy is required");
        }
        return tdlightConnectionRepository
            .findByIdAndUserAccount_Id(tdlightConnectionId, initiatedBy.getId())
            .filter(existing -> existing.getStatus() == TdlightConnectionStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("Active TDLight connection is required"));
    }

    private void closeExistingSession(Long tdlightConnectionId) {
        Optional.ofNullable(sessions.remove(tdlightConnectionId)).ifPresent(existing -> {
            closeInteractiveResources(existing);
        });
    }

    private ActiveQrAuthorizationSession requireSession(Long tdlightConnectionId) {
        ActiveQrAuthorizationSession session = sessions.get(tdlightConnectionId);
        if (session == null) {
            throw new IllegalStateException("TDLight session is not started");
        }
        return session;
    }

    private QrAuthorizationStatus toStatus(Long tdlightConnectionId, QrAuthorizationState state) {
        return new QrAuthorizationStatus(
            tdlightConnectionId,
            state.phase().name(),
            state.qrLink(),
            state.phoneNumber(),
            state.lastError(),
            state.updatedAt()
        );
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void closeInteractiveResources(ActiveQrAuthorizationSession session) {
        if (session == null) {
            return;
        }
        try {
            if (session.client() != null) {
                session.client().close();
            }
        } catch (Exception ignored) {
        } finally {
            session.setClient(null);
        }
        try {
            session.factory().close();
        } catch (Exception ignored) {
        }
    }

    private void syncAuthorizedConnection(ActiveQrAuthorizationSession session, QrAuthorizationState state) {
        if (state == null || state.tdlightConnectionId() == null) {
            return;
        }
        tdlightConnectionRepository.findById(state.tdlightConnectionId()).ifPresent(connection -> {
            try {
                TdApi.User me = session.client() == null ? null : session.client().getMeAsync().get();
                tdlightAccountBindingService.syncAuthorizedAccount(
                    connection,
                    new TdlightRuntimeAdapter.TdlightAuthorizedUser(
                        me == null ? null : String.valueOf(me.id),
                        firstActiveUsername(me == null ? null : me.usernames),
                        buildDisplayName(me)
                    )
                );
            } catch (Exception exception) {
                connection.markVerified(clock.instant());
                tdlightConnectionRepository.save(connection);
            }
        });
    }

    private String buildDisplayName(TdApi.User user) {
        if (user == null) {
            return null;
        }
        String displayName = ((user.firstName == null ? "" : user.firstName.trim())
            + " "
            + (user.lastName == null ? "" : user.lastName.trim())).trim();
        return displayName.isBlank() ? null : displayName;
    }

    private String firstActiveUsername(TdApi.Usernames usernames) {
        if (usernames == null || usernames.activeUsernames == null || usernames.activeUsernames.length == 0) {
            return null;
        }
        String username = usernames.activeUsernames[0];
        return username == null || username.isBlank() ? null : username.trim();
    }

    public record QrAuthorizationStatus(
        Long tdlightConnectionId,
        String phase,
        String qrLink,
        String phoneNumber,
        String lastError,
        Instant updatedAt
    ) {
    }

    private enum QrAuthorizationPhase {
        NOT_STARTED,
        STARTING,
        WAIT_QR_SCAN,
        WAIT_CODE,
        WAIT_PASSWORD,
        READY,
        FAILED
    }

    private record QrAuthorizationState(
        Long tdlightConnectionId,
        QrAuthorizationPhase phase,
        String qrLink,
        String phoneNumber,
        String lastError,
        Instant updatedAt
    ) {
    }

    private static final class ActiveQrAuthorizationSession {

        private final SimpleTelegramClientFactory factory;
        private final QrAuthorizationStateHolder state;
        private volatile SimpleTelegramClient client;
        private volatile CompletableFuture<String> pendingCode;
        private volatile CompletableFuture<String> pendingPassword;

        private ActiveQrAuthorizationSession(
            SimpleTelegramClientFactory factory,
            SimpleTelegramClient client,
            QrAuthorizationStateHolder state
        ) {
            this.factory = factory;
            this.client = client;
            this.state = state;
        }

        private SimpleTelegramClientFactory factory() {
            return factory;
        }

        private SimpleTelegramClient client() {
            return client;
        }

        private void setClient(SimpleTelegramClient client) {
            this.client = client;
        }

        private QrAuthorizationStateHolder state() {
            return state;
        }

        private CompletableFuture<String> pendingCode() {
            return pendingCode;
        }

        private void setPendingCode(CompletableFuture<String> pendingCode) {
            this.pendingCode = pendingCode;
        }

        private void clearPendingCode() {
            this.pendingCode = null;
        }

        private CompletableFuture<String> pendingPassword() {
            return pendingPassword;
        }

        private void setPendingPassword(CompletableFuture<String> pendingPassword) {
            this.pendingPassword = pendingPassword;
        }

        private void clearPendingPassword() {
            this.pendingPassword = null;
        }
    }

    private static final class QrAuthorizationStateHolder {

        private volatile QrAuthorizationState current;

        private QrAuthorizationStateHolder(QrAuthorizationState current) {
            this.current = current;
        }

        private QrAuthorizationState current() {
            return current;
        }

        private void update(
            QrAuthorizationPhase phase,
            String qrLink,
            String phoneNumber,
            String lastError,
            Instant updatedAt
        ) {
            this.current = new QrAuthorizationState(current.tdlightConnectionId(), phase, qrLink, phoneNumber, lastError, updatedAt);
        }
    }

    private static final class QrClientInteraction implements ClientInteraction {

        private final ActiveQrAuthorizationSession session;
        private final Clock clock;

        private QrClientInteraction(ActiveQrAuthorizationSession session, Clock clock) {
            this.session = session;
            this.clock = clock;
        }

        @Override
        public CompletableFuture<String> onParameterRequest(InputParameter parameter, ParameterInfo parameterInfo) {
            Instant now = clock.instant();
            QrAuthorizationStateHolder holder = session.state();
            if (parameter == InputParameter.NOTIFY_LINK && parameterInfo instanceof ParameterInfoNotifyLink notifyLink) {
                holder.update(QrAuthorizationPhase.WAIT_QR_SCAN, notifyLink.getLink(), null, null, now);
                return CompletableFuture.completedFuture("");
            }
            if (parameter == InputParameter.ASK_CODE && parameterInfo instanceof ParameterInfoCode codeInfo) {
                holder.update(QrAuthorizationPhase.WAIT_CODE, holder.current().qrLink(), codeInfo.getPhoneNumber(), null, now);
                CompletableFuture<String> pendingCode = new CompletableFuture<>();
                session.setPendingCode(pendingCode);
                return pendingCode;
            }
            if (parameter == InputParameter.ASK_PASSWORD) {
                holder.update(QrAuthorizationPhase.WAIT_PASSWORD, holder.current().qrLink(), null, null, now);
                CompletableFuture<String> pendingPassword = new CompletableFuture<>();
                session.setPendingPassword(pendingPassword);
                return pendingPassword;
            }
            holder.update(QrAuthorizationPhase.FAILED, holder.current().qrLink(), null, "Unsupported TDLight auth request: " + parameter, now);
            return CompletableFuture.failedFuture(new IllegalStateException("Unsupported TDLight auth request: " + parameter));
        }
    }
}
