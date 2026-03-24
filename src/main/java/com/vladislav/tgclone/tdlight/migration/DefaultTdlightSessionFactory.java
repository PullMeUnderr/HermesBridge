package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.TdlightRuntimeConfiguration;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;

public class DefaultTdlightSessionFactory implements TdlightSessionFactory {

    private static final String SESSION_ENCRYPTION_VERSION = "filesystem-session-v1";

    private final TdlightSessionStateStore tdlightSessionStateStore;
    private final TdlightSessionSecretStore tdlightSessionSecretStore;

    public DefaultTdlightSessionFactory(
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
                "filesystem-session:" + binding.sessionKey(),
                SESSION_ENCRYPTION_VERSION,
                fingerprint(binding, nativeRuntimeDescriptor)
            );

        boolean restoredFromExistingSession = existingBinding != null;
        return new DefaultTdlightRuntimeSession(
            new TdlightRuntimeAdapter.TdlightRuntimeSessionHandle(connection.getId(), binding.sessionKey()),
            binding,
            sessionEnvelope,
            restoredFromExistingSession
        );
    }

    private String fingerprint(
        TdlightSessionBinding binding,
        TdlightNativeLibraryBootstrap.NativeRuntimeDescriptor nativeRuntimeDescriptor
    ) {
        return "%s|%s|%s".formatted(
            binding.sessionKey(),
            binding.databaseDirectory(),
            nativeRuntimeDescriptor.libraryPath()
        );
    }

    private record DefaultTdlightRuntimeSession(
        TdlightRuntimeAdapter.TdlightRuntimeSessionHandle sessionHandle,
        TdlightSessionBinding sessionBinding,
        TdlightSessionEnvelope sessionEnvelope,
        boolean restoredFromExistingSession
    ) implements TdlightRuntimeSession {

        @Override
        public void close() {
        }
    }
}
