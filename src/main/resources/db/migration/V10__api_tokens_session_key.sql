alter table api_tokens
    add column session_key varchar(64);

create index idx_api_tokens_session_key
    on api_tokens (session_key, revoked);
