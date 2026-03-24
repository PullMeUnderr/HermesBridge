package com.vladislav.tgclone.tdlight.connection;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TdlightConnectionRepository extends JpaRepository<TdlightConnection, Long> {

    Optional<TdlightConnection> findByIdAndUserAccount_Id(Long id, Long userAccountId);

    Optional<TdlightConnection> findFirstByUserAccount_IdAndStatusOrderByCreatedAtDesc(
        Long userAccountId,
        TdlightConnectionStatus status
    );

    Optional<TdlightConnection> findFirstByTdlightUserIdAndUserAccount_IdNot(String tdlightUserId, Long userAccountId);

    List<TdlightConnection> findAllByUserAccount_IdOrderByCreatedAtDesc(Long userAccountId);
}
