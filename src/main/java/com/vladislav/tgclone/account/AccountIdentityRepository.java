package com.vladislav.tgclone.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountIdentityRepository extends JpaRepository<AccountIdentity, Long> {

    Optional<AccountIdentity> findByProviderAndProviderUserKey(String provider, String providerUserKey);

    Optional<AccountIdentity> findByUserAccount_IdAndProvider(Long userAccountId, String provider);

    boolean existsByUserAccount_IdAndProvider(Long userAccountId, String provider);
}
