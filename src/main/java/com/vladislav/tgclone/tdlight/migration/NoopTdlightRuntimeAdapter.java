package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.TdlightRuntimeConfiguration;
import com.vladislav.tgclone.tdlight.condition.ConditionalOnTdlightRealMode;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnTdlightRealMode
public class NoopTdlightRuntimeAdapter implements TdlightRuntimeAdapter {

    private final TdlightNativeLibraryBootstrap tdlightNativeLibraryBootstrap;
    private final TdlightSessionFactory tdlightSessionFactory;

    public NoopTdlightRuntimeAdapter(
        TdlightNativeLibraryBootstrap tdlightNativeLibraryBootstrap,
        TdlightSessionFactory tdlightSessionFactory
    ) {
        this.tdlightNativeLibraryBootstrap = tdlightNativeLibraryBootstrap;
        this.tdlightSessionFactory = tdlightSessionFactory;
    }

    @Override
    public TdlightRuntimeSessionContext openSession(
        TdlightConnection connection,
        TdlightRuntimeConfiguration runtimeConfiguration
    ) {
        TdlightNativeLibraryBootstrap.NativeRuntimeDescriptor nativeRuntimeDescriptor =
            tdlightNativeLibraryBootstrap.ensureLoaded(runtimeConfiguration);
        TdlightSessionFactory.TdlightRuntimeSession runtimeSession = tdlightSessionFactory.createSession(
            connection,
            runtimeConfiguration,
            nativeRuntimeDescriptor
        );
        return new TdlightRuntimeSessionContext(
            runtimeSession.sessionHandle(),
            runtimeSession.sessionBinding(),
            runtimeSession.sessionEnvelope(),
            runtimeSession.restoredFromExistingSession()
        );
    }

    @Override
    public void closeSession(TdlightRuntimeSessionContext sessionContext) {
    }

    @Override
    public TdlightPublicChannelClient.TdlightResolvedChannel resolvePublicChannel(
        TdlightRuntimeSessionContext sessionContext,
        TdlightPublicChannelClient.TdlightChannelReference reference
    ) {
        throw notImplemented("resolvePublicChannel", sessionContext);
    }

    @Override
    public List<TdlightPublicChannelClient.TdlightFetchedPost> fetchNewPosts(
        TdlightRuntimeSessionContext sessionContext,
        TdlightPublicChannelClient.TdlightResolvedChannel channel,
        TdlightPublicChannelClient.TdlightFetchCursor cursor,
        int limit
    ) {
        throw notImplemented("fetchNewPosts", sessionContext);
    }

    @Override
    public TdlightPublicChannelClient.TdlightFetchedMedia fetchMedia(
        TdlightRuntimeSessionContext sessionContext,
        TdlightPublicChannelClient.TdlightResolvedChannel channel,
        TdlightPublicChannelClient.TdlightFetchedPost post,
        TdlightPublicChannelClient.TdlightFetchedMediaReference mediaReference
    ) {
        throw notImplemented("fetchMedia", sessionContext);
    }

    @Override
    public TdlightAuthorizedUser getCurrentUser(TdlightRuntimeSessionContext sessionContext) {
        throw notImplemented("getCurrentUser", sessionContext);
    }

    private IllegalStateException notImplemented(
        String operation,
        TdlightRuntimeSessionContext sessionContext
    ) {
        return new IllegalStateException(
            "TDLight runtime adapter skeleton opened session '%s' for connection %s, but operation '%s' "
                .formatted(
                    sessionContext.sessionHandle().sessionKey(),
                    sessionContext.sessionHandle().tdlightConnectionId(),
                    operation
                )
                + " is not implemented yet. restoredFromExistingSession="
                + sessionContext.restoredFromExistingSession()
                + ", sessionFingerprint="
                + (sessionContext.sessionEnvelope() == null ? null : sessionContext.sessionEnvelope().sessionFingerprint())
                + ". Replace NoopTdlightRuntimeAdapter with an SDK-backed adapter."
        );
    }
}
