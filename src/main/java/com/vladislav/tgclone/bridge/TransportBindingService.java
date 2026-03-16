package com.vladislav.tgclone.bridge;

import com.vladislav.tgclone.account.UserAccount;
import com.vladislav.tgclone.common.NotFoundException;
import com.vladislav.tgclone.conversation.Conversation;
import com.vladislav.tgclone.conversation.ConversationMember;
import com.vladislav.tgclone.conversation.ConversationService;
import com.vladislav.tgclone.security.AuthenticatedUser;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransportBindingService {

    private final TransportBindingRepository transportBindingRepository;
    private final ConversationService conversationService;
    private final Clock clock;

    public TransportBindingService(
        TransportBindingRepository transportBindingRepository,
        ConversationService conversationService,
        Clock clock
    ) {
        this.transportBindingRepository = transportBindingRepository;
        this.conversationService = conversationService;
        this.clock = clock;
    }

    @Transactional
    public TransportBinding createBinding(
        AuthenticatedUser authenticatedUser,
        Long conversationId,
        BridgeTransport transport,
        String externalChatId
    ) {
        if (transport == null) {
            throw new IllegalArgumentException("transport is required");
        }

        Conversation conversation = conversationService.requireManagerMembership(
            authenticatedUser,
            conversationId
        ).getConversation();
        return upsertBinding(conversation, transport, normalizeExternalChatId(externalChatId));
    }

    @Transactional
    public ConversationMember createConversationWithTelegramBinding(
        UserAccount userAccount,
        String title,
        String externalChatId
    ) {
        ConversationMember membership = conversationService.createConversation(userAccount, title);
        upsertBinding(
            membership.getConversation(),
            BridgeTransport.TELEGRAM,
            normalizeExternalChatId(externalChatId)
        );
        return membership;
    }

    @Transactional
    public TransportBinding createTelegramBinding(
        UserAccount userAccount,
        Long conversationId,
        String externalChatId
    ) {
        if (userAccount == null || userAccount.getId() == null) {
            throw new IllegalArgumentException("userAccount is required");
        }

        return createBinding(
            AuthenticatedUser.from(userAccount),
            conversationId,
            BridgeTransport.TELEGRAM,
            externalChatId
        );
    }

    @Transactional(readOnly = true)
    public List<TransportBinding> listBindings(AuthenticatedUser authenticatedUser, Long conversationId) {
        conversationService.getConversationAccessible(authenticatedUser, conversationId);
        return transportBindingRepository.findAllByConversation_Id(conversationId);
    }

    @Transactional
    public TransportBinding updateBinding(
        AuthenticatedUser authenticatedUser,
        Long bindingId,
        boolean active
    ) {
        TransportBinding binding = transportBindingRepository.findById(bindingId)
            .orElseThrow(() -> new NotFoundException("Binding %s not found".formatted(bindingId)));

        conversationService.requireManagerMembership(authenticatedUser, binding.getConversation().getId());
        binding.setActive(active);
        return transportBindingRepository.save(binding);
    }

    private TransportBinding upsertBinding(
        Conversation conversation,
        BridgeTransport transport,
        String externalChatId
    ) {
        List<TransportBinding> existingBindings = transportBindingRepository.findAllByTransportAndExternalChatId(
            transport,
            externalChatId
        );

        for (TransportBinding existingBinding : existingBindings) {
            if (!existingBinding.getConversation().getId().equals(conversation.getId())) {
                throw new IllegalArgumentException("This Telegram chat is already connected to another conversation");
            }

            if (!existingBinding.isActive()) {
                existingBinding.setActive(true);
                return transportBindingRepository.save(existingBinding);
            }
            return existingBinding;
        }

        TransportBinding binding = new TransportBinding(
            conversation,
            transport,
            externalChatId,
            true,
            clock.instant()
        );
        return transportBindingRepository.save(binding);
    }

    private String normalizeExternalChatId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("externalChatId is required");
        }
        return value.trim();
    }
}
