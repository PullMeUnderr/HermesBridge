package com.vladislav.tgclone.tdlight.connection;

import com.vladislav.tgclone.account.UserAccount;
import java.util.List;
import java.util.Optional;

public interface TdlightConnectionService {

    Optional<TdlightConnectionDescriptor> findPrimaryConnection(UserAccount userAccount);

    List<TdlightConnectionDescriptor> listConnections(UserAccount userAccount);

    TdlightConnectionDescriptor createDevelopmentConnection(
        UserAccount userAccount,
        String phoneMask,
        String tdlightUserId,
        boolean forceNew
    );

    boolean hasActiveConnection(UserAccount userAccount);
}
