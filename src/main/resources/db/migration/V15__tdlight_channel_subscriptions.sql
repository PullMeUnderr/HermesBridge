create table tdlight_channel_subscriptions (
    id bigserial primary key,
    user_account_id bigint not null references user_accounts(id),
    tdlight_connection_id bigint not null references tdlight_connections(id),
    conversation_id bigint not null references conversations(id),
    telegram_channel_id varchar(255) not null,
    telegram_channel_handle varchar(255),
    channel_title varchar(255) not null,
    status varchar(30) not null,
    subscribed_at timestamp with time zone not null,
    last_synced_remote_message_id varchar(255),
    last_synced_at timestamp with time zone,
    last_error text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index uk_tdlight_channel_subscription_user_channel
    on tdlight_channel_subscriptions (user_account_id, telegram_channel_id);

create index idx_tdlight_channel_subscription_status_created
    on tdlight_channel_subscriptions (status, created_at);
