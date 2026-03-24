package com.vladislav.tgclone.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkTokenRepository extends JpaRepository<LinkToken, Long> {

    Optional<LinkToken> findByTokenHash(String tokenHash);

    List<LinkToken> findAllByUserAccount_IdAndProviderAndConsumedAtIsNull(Long userAccountId, String provider);
}
