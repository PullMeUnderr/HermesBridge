package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.account.UserAccount;
import java.util.List;

public interface TdlightChannelSubscriptionService {

    List<TdlightAvailableChannelSummary> listAvailableChannels(UserAccount userAccount, Long tdlightConnectionId);

    List<TdlightChannelSubscriptionSummary> listSubscriptions(UserAccount userAccount);

    TdlightChannelSubscriptionSummary subscribe(UserAccount userAccount, TdlightChannelSubscriptionRequest request);

    void disconnectByConversation(UserAccount userAccount, Long conversationId);
}
