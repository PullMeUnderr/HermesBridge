package com.vladislav.tgclone.tdlight.migration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMigrationRepository extends JpaRepository<ChannelMigration, Long> {

    Optional<ChannelMigration> findByIdAndInitiatedByUser_Id(Long id, Long initiatedByUserId);

    Optional<ChannelMigration> findTopByInitiatedByUser_IdAndSourceChannelIdOrderByCreatedAtDesc(
        Long initiatedByUserId,
        String sourceChannelId
    );

    List<ChannelMigration> findAllByInitiatedByUser_IdOrderByCreatedAtDesc(Long initiatedByUserId);

    List<ChannelMigration> findAllByStatusOrderByCreatedAtAsc(ChannelMigrationStatus status);

    List<ChannelMigration> findAllByStatusInOrderByCreatedAtAsc(List<ChannelMigrationStatus> statuses);

    List<ChannelMigration> findAllByRetentionUntilBefore(Instant threshold);
}
