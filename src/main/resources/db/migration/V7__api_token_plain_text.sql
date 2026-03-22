alter table api_tokens
    add column plain_text_token varchar(255);

create unique index uk_api_tokens_plain_text_token
    on api_tokens (plain_text_token);
