package com.vladislav.tgclone.entitlement;

import com.vladislav.tgclone.account.UserAccount;

public interface EntitlementService {

    boolean hasEntitlement(UserAccount userAccount, FeatureEntitlement entitlement);
}
