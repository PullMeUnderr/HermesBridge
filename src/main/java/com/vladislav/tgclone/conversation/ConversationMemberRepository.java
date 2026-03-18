package com.vladislav.tgclone.conversation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    Optional<ConversationMember> findByConversation_IdAndUserAccount_Id(Long conversationId, Long userId);

    List<ConversationMember> findAllByConversation_IdOrderByJoinedAtAsc(Long conversationId);

    List<ConversationMember> findAllByUserAccount_IdOrderByConversation_CreatedAtDesc(Long userId);

    List<ConversationMember> findTop5ByUserAccount_IdAndRoleOrderByConversation_CreatedAtDesc(
        Long userId,
        ConversationMemberRole role
    );

    @Modifying
    @Query("""
        update ConversationMember member
        set member.lastReadMessageCreatedAt = :timestamp
        where member.conversation.id = :conversationId
          and member.userAccount.id = :userId
          and (member.lastReadMessageCreatedAt is null or member.lastReadMessageCreatedAt < :timestamp)
    """)
    int markReadUpTo(
        @Param("conversationId") Long conversationId,
        @Param("userId") Long userId,
        @Param("timestamp") java.time.Instant timestamp
    );

    void deleteAllByConversation_Id(Long conversationId);
}
