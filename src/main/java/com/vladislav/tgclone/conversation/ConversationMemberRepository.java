package com.vladislav.tgclone.conversation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    Optional<ConversationMember> findByConversation_IdAndUserAccount_Id(Long conversationId, Long userId);

    List<ConversationMember> findAllByConversation_IdOrderByJoinedAtAsc(Long conversationId);

    List<ConversationMember> findAllByUserAccount_IdOrderByConversation_CreatedAtDesc(Long userId);
}
