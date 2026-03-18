alter table conversation_members
    add column muted boolean not null default false,
    add column last_read_message_created_at timestamp with time zone;

update conversation_members
set last_read_message_created_at = joined_at
where last_read_message_created_at is null;
