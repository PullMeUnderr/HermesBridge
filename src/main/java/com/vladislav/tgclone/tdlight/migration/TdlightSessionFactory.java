package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.TdlightRuntimeConfiguration;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;

public interface TdlightSessionFactory {

    TdlightRuntimeSession createSession(
        TdlightConnection connection,
        TdlightRuntimeConfiguration runtimeConfiguration,
        TdlightNativeLibraryBootstrap.NativeRuntimeDescriptor nativeRuntimeDescriptor
    );

    interface TdlightRuntimeSession extends AutoCloseable {

        TdlightRuntimeAdapter.TdlightRuntimeSessionHandle sessionHandle();

        TdlightSessionBinding sessionBinding();

        TdlightSessionEnvelope sessionEnvelope();

        boolean restoredFromExistingSession();

        @Override
        void close();
    }
}
