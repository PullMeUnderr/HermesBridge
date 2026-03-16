package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.bridge.BridgeTransport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findAllByConversation_IdOrderByCreatedAtAsc(Long conversationId);

    Optional<ConversationMessage> findBySourceTransportAndSourceChatIdAndSourceMessageId(
        BridgeTransport sourceTransport,
        String sourceChatId,
        String sourceMessageId
    );
}
