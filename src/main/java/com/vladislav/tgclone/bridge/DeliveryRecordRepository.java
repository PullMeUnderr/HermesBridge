package com.vladislav.tgclone.bridge;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, Long> {

    boolean existsByConversationMessage_IdAndTargetTransportAndTargetChatId(
        Long conversationMessageId,
        BridgeTransport targetTransport,
        String targetChatId
    );
}
