package com.vladislav.tgclone.telegram;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TelegramPrivateDialogStateService {

    private final Map<String, PendingPrivateAction> pendingActions = new ConcurrentHashMap<>();

    public void beginCreateChat(String privateChatId) {
        pendingActions.put(privateChatId, PendingPrivateAction.CREATE_CHAT_TITLE);
    }

    public void beginJoinInvite(String privateChatId) {
        pendingActions.put(privateChatId, PendingPrivateAction.JOIN_INVITE_CODE);
    }

    public PendingPrivateAction get(String privateChatId) {
        return pendingActions.get(privateChatId);
    }

    public void clear(String privateChatId) {
        pendingActions.remove(privateChatId);
    }
}
