package com.vladislav.tgclone.tdlight.migration;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TdlightSessionSecretRepository extends JpaRepository<TdlightSessionSecretEntity, Long> {

    Optional<TdlightSessionSecretEntity> findFirstByTdlightConnectionIdAndRevokedAtIsNullOrderByUpdatedAtDesc(
        Long tdlightConnectionId
    );
}
