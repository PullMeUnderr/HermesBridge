package com.vladislav.tgclone.bridge;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, Long> {

    boolean existsByConversationMessage_IdAndTargetTransportAndTargetChatId(
        Long conversationMessageId,
        BridgeTransport targetTransport,
        String targetChatId
    );

    Optional<DeliveryRecord> findByConversationMessage_IdAndTargetTransportAndTargetChatId(
        Long conversationMessageId,
        BridgeTransport targetTransport,
        String targetChatId
    );

    Optional<DeliveryRecord> findByTargetTransportAndTargetChatIdAndTargetMessageId(
        BridgeTransport targetTransport,
        String targetChatId,
        String targetMessageId
    );
}
