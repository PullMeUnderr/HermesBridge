package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.connection.TdlightConnection;
import java.util.Optional;

public interface TdlightSessionStateStore {

    Optional<TdlightSessionBinding> findBinding(TdlightConnection connection);

    TdlightSessionBinding createBinding(
        TdlightConnection connection,
        String databaseDirectory,
        String filesDirectory
    );

    void revokeBinding(TdlightConnection connection);
}
