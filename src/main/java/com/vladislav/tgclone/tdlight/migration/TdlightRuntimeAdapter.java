package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.TdlightRuntimeConfiguration;
import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import java.util.List;

public interface TdlightRuntimeAdapter {

    TdlightRuntimeSessionContext openSession(
        TdlightConnection connection,
        TdlightRuntimeConfiguration runtimeConfiguration
    );

    void closeSession(TdlightRuntimeSessionContext sessionContext);

    TdlightPublicChannelClient.TdlightResolvedChannel resolvePublicChannel(
        TdlightRuntimeSessionContext sessionContext,
        TdlightPublicChannelClient.TdlightChannelReference reference
    );

    List<TdlightPublicChannelClient.TdlightAvailableChannel> listAvailablePublicChannels(
        TdlightRuntimeSessionContext sessionContext
    );

    List<TdlightPublicChannelClient.TdlightFetchedPost> fetchNewPosts(
        TdlightRuntimeSessionContext sessionContext,
        TdlightPublicChannelClient.TdlightResolvedChannel channel,
        TdlightPublicChannelClient.TdlightFetchCursor cursor,
        int limit
    );

    TdlightPublicChannelClient.TdlightFetchedMedia fetchMedia(
        TdlightRuntimeSessionContext sessionContext,
        TdlightPublicChannelClient.TdlightResolvedChannel channel,
        TdlightPublicChannelClient.TdlightFetchedPost post,
        TdlightPublicChannelClient.TdlightFetchedMediaReference mediaReference
    );

    TdlightPublicChannelClient.TdlightFetchedMedia fetchChannelAvatar(
        TdlightRuntimeSessionContext sessionContext,
        TdlightPublicChannelClient.TdlightResolvedChannel channel
    );

    TdlightAuthorizedUser getCurrentUser(TdlightRuntimeSessionContext sessionContext);

    record TdlightRuntimeSessionHandle(
        Long tdlightConnectionId,
        String sessionKey
    ) {
    }

    record TdlightRuntimeSessionContext(
        TdlightRuntimeSessionHandle sessionHandle,
        TdlightSessionBinding sessionBinding,
        TdlightSessionEnvelope sessionEnvelope,
        boolean restoredFromExistingSession
    ) {
    }

    record TdlightAuthorizedUser(
        String telegramUserId,
        String telegramUsername,
        String displayName
    ) {
    }
}
