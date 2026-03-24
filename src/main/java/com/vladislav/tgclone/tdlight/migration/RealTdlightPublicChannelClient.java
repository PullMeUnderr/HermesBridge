package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.TdlightClientMode;
import com.vladislav.tgclone.tdlight.TdlightProperties;
import com.vladislav.tgclone.tdlight.TdlightRuntimeConfiguration;
import com.vladislav.tgclone.tdlight.condition.ConditionalOnTdlightRealMode;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnTdlightRealMode
public class RealTdlightPublicChannelClient implements TdlightPublicChannelClient {

    private final ReentrantLock runtimeOperationLock = new ReentrantLock();

    private final TdlightProperties tdlightProperties;
    private final TdlightRuntimeAdapter tdlightRuntimeAdapter;

    public RealTdlightPublicChannelClient(
        TdlightProperties tdlightProperties,
        TdlightRuntimeAdapter tdlightRuntimeAdapter
    ) {
        this.tdlightProperties = tdlightProperties;
        this.tdlightRuntimeAdapter = tdlightRuntimeAdapter;
    }

    @Override
    public TdlightResolvedChannel resolvePublicChannel(
        TdlightConnection connection,
        TdlightChannelReference reference
    ) {
        TdlightRuntimeConfiguration runtimeConfiguration = requireRuntimeConfiguration();
        return withSession(
            connection,
            runtimeConfiguration,
            sessionHandle -> tdlightRuntimeAdapter.resolvePublicChannel(sessionHandle, reference)
        );
    }

    @Override
    public List<TdlightFetchedPost> fetchNewPosts(
        TdlightConnection connection,
        TdlightResolvedChannel channel,
        TdlightFetchCursor cursor,
        int limit
    ) {
        TdlightRuntimeConfiguration runtimeConfiguration = requireRuntimeConfiguration();
        return withSession(
            connection,
            runtimeConfiguration,
            sessionHandle -> tdlightRuntimeAdapter.fetchNewPosts(sessionHandle, channel, cursor, limit)
        );
    }

    @Override
    public TdlightFetchedMedia fetchMedia(
        TdlightConnection connection,
        TdlightResolvedChannel channel,
        TdlightFetchedPost post,
        TdlightFetchedMediaReference mediaReference
    ) {
        TdlightRuntimeConfiguration runtimeConfiguration = requireRuntimeConfiguration();
        return withSession(
            connection,
            runtimeConfiguration,
            sessionHandle -> tdlightRuntimeAdapter.fetchMedia(sessionHandle, channel, post, mediaReference)
        );
    }

    private TdlightRuntimeConfiguration requireRuntimeConfiguration() {
        List<String> missing = new ArrayList<>();
        if (tdlightProperties.publicChannelClientMode() != TdlightClientMode.REAL) {
            missing.add("app.tdlight.public-channel-client-mode=REAL");
        }
        if (isBlank(tdlightProperties.databaseDirectory())) {
            missing.add("app.tdlight.database-directory");
        }
        if (isBlank(tdlightProperties.filesDirectory())) {
            missing.add("app.tdlight.files-directory");
        }
        if (tdlightProperties.apiId() == null) {
            missing.add("app.tdlight.api-id");
        }
        if (isBlank(tdlightProperties.apiHash())) {
            missing.add("app.tdlight.api-hash");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "TDLight REAL client mode is selected, but runtime configuration is incomplete: "
                    + String.join(", ", missing)
            );
        }

        return new TdlightRuntimeConfiguration(
            trimToNull(tdlightProperties.libraryPath()),
            trimToNull(tdlightProperties.nativeWorkdir()),
            tdlightProperties.databaseDirectory().trim(),
            tdlightProperties.filesDirectory().trim(),
            tdlightProperties.apiId(),
            tdlightProperties.apiHash().trim(),
            tdlightProperties.systemLanguageCode(),
            tdlightProperties.deviceModel(),
            tdlightProperties.applicationVersion()
        );
    }

    private <T> T withSession(
        TdlightConnection connection,
        TdlightRuntimeConfiguration runtimeConfiguration,
        TdlightRuntimeOperation<T> operation
    ) {
        runtimeOperationLock.lock();
        try {
            TdlightRuntimeAdapter.TdlightRuntimeSessionContext sessionContext = tdlightRuntimeAdapter.openSession(
                connection,
                runtimeConfiguration
            );
            try {
                return operation.execute(sessionContext);
            } finally {
                tdlightRuntimeAdapter.closeSession(sessionContext);
            }
        } finally {
            runtimeOperationLock.unlock();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    @FunctionalInterface
    interface TdlightRuntimeOperation<T> {
        T execute(TdlightRuntimeAdapter.TdlightRuntimeSessionContext sessionContext);
    }
}
