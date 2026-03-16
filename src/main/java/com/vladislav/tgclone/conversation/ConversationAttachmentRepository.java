package com.vladislav.tgclone.conversation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationAttachmentRepository extends JpaRepository<ConversationAttachment, Long> {

    Optional<ConversationAttachment> findById(Long id);
}
