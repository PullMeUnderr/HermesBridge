package com.vladislav.tgclone.tdlight.migration;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TdlightSessionBindingRepository extends JpaRepository<TdlightSessionBindingEntity, Long> {

    Optional<TdlightSessionBindingEntity> findByTdlightConnectionIdAndRevokedAtIsNull(Long tdlightConnectionId);
}
