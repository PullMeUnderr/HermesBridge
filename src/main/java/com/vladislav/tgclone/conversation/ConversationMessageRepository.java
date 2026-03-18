package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.bridge.BridgeTransport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    @EntityGraph(attributePaths = "attachments")
    List<ConversationMessage> findAllByConversation_IdOrderByCreatedAtAsc(Long conversationId);

    @EntityGraph(attributePaths = "attachments")
    Optional<ConversationMessage> findBySourceTransportAndSourceChatIdAndSourceMessageId(
        BridgeTransport sourceTransport,
        String sourceChatId,
        String sourceMessageId
    );

    Optional<ConversationMessage> findByIdAndConversation_Id(Long id, Long conversationId);

    @Modifying
    @Query("update ConversationMessage message set message.replyToMessage = null where message.conversation.id = :conversationId")
    void clearReplyReferences(@Param("conversationId") Long conversationId);

    void deleteAllByConversation_Id(Long conversationId);
}
