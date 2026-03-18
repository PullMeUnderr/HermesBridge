alter table conversations
    add column avatar_storage_key varchar(500);

alter table conversations
    add column avatar_mime_type varchar(255);

alter table conversations
    add column avatar_original_filename varchar(255);

alter table conversations
    add column avatar_updated_at timestamp with time zone;
