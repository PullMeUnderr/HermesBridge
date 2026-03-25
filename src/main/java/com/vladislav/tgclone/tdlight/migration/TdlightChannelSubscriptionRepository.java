package com.vladislav.tgclone.tdlight.migration;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TdlightChannelSubscriptionRepository extends JpaRepository<TdlightChannelSubscription, Long> {

    List<TdlightChannelSubscription> findAllByUserAccount_IdOrderByCreatedAtDesc(Long userAccountId);

    List<TdlightChannelSubscription> findAllByStatusOrderByCreatedAtAsc(TdlightChannelSubscriptionStatus status);

    List<TdlightChannelSubscription> findAllByStatusInOrderByCreatedAtAsc(
        List<TdlightChannelSubscriptionStatus> statuses
    );

    Optional<TdlightChannelSubscription> findByIdAndUserAccount_Id(Long id, Long userAccountId);

    Optional<TdlightChannelSubscription> findByUserAccount_IdAndTelegramChannelId(Long userAccountId, String telegramChannelId);

    Optional<TdlightChannelSubscription> findByUserAccount_IdAndConversationId(Long userAccountId, Long conversationId);
}
