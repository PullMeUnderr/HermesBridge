package com.vladislav.tgclone.bridge;

import com.vladislav.tgclone.conversation.ConversationMessage;

public interface DeliveryGateway {

    BridgeTransport transport();

    String deliver(TransportBinding binding, ConversationMessage message);
}
