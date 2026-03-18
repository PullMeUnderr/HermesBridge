package com.vladislav.tgclone.bridge;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportBindingRepository extends JpaRepository<TransportBinding, Long> {

    List<TransportBinding> findAllByConversation_Id(Long conversationId);

    List<TransportBinding> findAllByConversation_IdAndActiveTrue(Long conversationId);

    List<TransportBinding> findAllByTransportAndExternalChatId(
        BridgeTransport transport,
        String externalChatId
    );

    Optional<TransportBinding> findByTransportAndExternalChatIdAndActiveTrue(
        BridgeTransport transport,
        String externalChatId
    );

    void deleteAllByConversation_Id(Long conversationId);
}
