package com.vladislav.tgclone.entitlement;

import com.vladislav.tgclone.account.UserAccount;
import org.springframework.stereotype.Service;

@Service
public class AllowAllEntitlementService implements EntitlementService {

    @Override
    public boolean hasEntitlement(UserAccount userAccount, FeatureEntitlement entitlement) {
        return true;
    }
}
