alter table conversation_messages
    add column reply_to_message_id bigint references conversation_messages(id);

create index idx_conversation_messages_reply_to on conversation_messages (reply_to_message_id);
