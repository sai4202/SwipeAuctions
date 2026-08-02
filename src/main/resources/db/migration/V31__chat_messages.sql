-- V31 — Support chat: one thread per user (all their messages plus every admin reply), admin sees
-- every thread. Simple two-party model, no groups/attachments. DDL generated from the ChatMessage
-- entity so ddl-auto=validate matches.

create table chat_messages (
    id uuid not null,
    user_id uuid not null,
    sender varchar(10) not null check (sender in ('USER','ADMIN')),
    body text not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id)
);

create index idx_chat_messages_user on chat_messages (user_id, created_at);

alter table if exists chat_messages
    add constraint fk_chat_messages_user foreign key (user_id) references users;
