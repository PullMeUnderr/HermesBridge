package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.bridge.BridgeTransport;
import java.time.Instant;
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

    Optional<ConversationMessage> findTopByConversation_IdOrderByCreatedAtDescIdDesc(Long conversationId);

    @Query("""
        select count(message)
        from ConversationMessage message
        where message.conversation.id = :conversationId
          and (message.authorUser is null or message.authorUser.id <> :userId)
    """)
    long countUnreadForUser(
        @Param("conversationId") Long conversationId,
        @Param("userId") Long userId
    );

    @Query("""
        select count(message)
        from ConversationMessage message
        where message.conversation.id = :conversationId
          and message.createdAt > :lastRead
          and (message.authorUser is null or message.authorUser.id <> :userId)
    """)
    long countUnreadForUserAfter(
        @Param("conversationId") Long conversationId,
        @Param("userId") Long userId,
        @Param("lastRead") Instant lastRead
    );

    @Modifying
    @Query("update ConversationMessage message set message.replyToMessage = null where message.conversation.id = :conversationId")
    void clearReplyReferences(@Param("conversationId") Long conversationId);

    void deleteAllByConversation_Id(Long conversationId);
}
