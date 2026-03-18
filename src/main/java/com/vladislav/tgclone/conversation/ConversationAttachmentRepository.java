package com.vladislav.tgclone.conversation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationAttachmentRepository extends JpaRepository<ConversationAttachment, Long> {

    Optional<ConversationAttachment> findById(Long id);

    List<ConversationAttachment> findAllByMessage_Conversation_Id(Long conversationId);

    void deleteAllByMessage_Conversation_Id(Long conversationId);
}
