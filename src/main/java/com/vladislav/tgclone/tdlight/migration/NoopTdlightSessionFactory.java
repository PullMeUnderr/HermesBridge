package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.TdlightRuntimeConfiguration;
import com.vladislav.tgclone.tdlight.condition.ConditionalOnTdlightRealMode;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnTdlightRealMode
public class NoopTdlightSessionFactory implements TdlightSessionFactory {

    private final TdlightSessionStateStore tdlightSessionStateStore;
    private final TdlightSessionSecretStore tdlightSessionSecretStore;

    public NoopTdlightSessionFactory(
        TdlightSessionStateStore tdlightSessionStateStore,
        TdlightSessionSecretStore tdlightSessionSecretStore
    ) {
        this.tdlightSessionStateStore = tdlightSessionStateStore;
        this.tdlightSessionSecretStore = tdlightSessionSecretStore;
    }

    @Override
    public TdlightRuntimeSession createSession(
        TdlightConnection connection,
        TdlightRuntimeConfiguration runtimeConfiguration,
        TdlightNativeLibraryBootstrap.NativeRuntimeDescriptor nativeRuntimeDescriptor
    ) {
        TdlightSessionBinding existingBinding = tdlightSessionStateStore.findBinding(connection).orElse(null);
        TdlightSessionEnvelope existingEnvelope = tdlightSessionSecretStore.findActiveSession(connection).orElse(null);

        TdlightSessionBinding binding = existingBinding != null
            ? existingBinding
            : tdlightSessionStateStore.createBinding(
                connection,
                runtimeConfiguration.databaseDirectory(),
                runtimeConfiguration.filesDirectory()
            );

        TdlightSessionEnvelope sessionEnvelope = existingEnvelope != null
            ? existingEnvelope
            : tdlightSessionSecretStore.writeSession(
                connection,
                binding.sessionKey(),
                "pending-runtime-bootstrap:" + binding.sessionKey(),
                "noop-local-v1",
                "pending-runtime-bootstrap"
            );

        boolean restoredFromExistingSession = existingBinding != null && existingEnvelope != null;
        return new NoopTdlightRuntimeSession(
            new TdlightRuntimeAdapter.TdlightRuntimeSessionHandle(
                connection.getId(),
                binding.sessionKey()
            ),
            binding,
            sessionEnvelope,
            restoredFromExistingSession,
            nativeRuntimeDescriptor.libraryPath(),
            binding.databaseDirectory(),
            binding.filesDirectory()
        );
    }

    private record NoopTdlightRuntimeSession(
        TdlightRuntimeAdapter.TdlightRuntimeSessionHandle sessionHandle,
        TdlightSessionBinding sessionBinding,
        TdlightSessionEnvelope sessionEnvelope,
        boolean restoredFromExistingSession,
        String libraryPath,
        String databaseDirectory,
        String filesDirectory
    ) implements TdlightRuntimeSession {

        @Override
        public void close() {
        }
    }
}
