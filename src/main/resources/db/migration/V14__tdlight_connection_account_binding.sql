alter table tdlight_connections
    add column if not exists tdlight_username varchar(255);

alter table tdlight_connections
    add column if not exists tdlight_display_name varchar(255);

create index if not exists idx_tdlight_connections_tdlight_user_id
    on tdlight_connections (tdlight_user_id);
