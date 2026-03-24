package com.vladislav.tgclone.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    Optional<ApiToken> findByTokenHashAndRevokedFalse(String tokenHash);

    Optional<ApiToken> findFirstByUserAccount_IdAndLabelAndRevokedFalseOrderByCreatedAtDesc(Long userAccountId, String label);

    List<ApiToken> findAllByUserAccount_IdAndRevokedFalse(Long userAccountId);

    List<ApiToken> findAllBySessionKeyAndRevokedFalse(String sessionKey);
}
