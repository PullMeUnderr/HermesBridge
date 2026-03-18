package com.vladislav.tgclone.conversation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationInviteRepository extends JpaRepository<ConversationInvite, Long> {

    Optional<ConversationInvite> findByInviteHash(String inviteHash);

    void deleteAllByConversation_Id(Long conversationId);
}
