-- V6 — Minimal dispute tracking: raise / list / resolve. No wallet-freeze/refund automation yet
-- (that's a future phase). DDL generated from the Dispute entity so ddl-auto=validate matches.

create table disputes (
    created_at timestamp(6) not null,
    resolved_at timestamp(6),
    updated_at timestamp(6) not null,
    auction_id uuid not null,
    id uuid not null,
    raised_by_id uuid not null,
    status varchar(255) not null check ((status in ('OPEN','IN_REVIEW','RESOLVED'))),
    admin_notes TEXT,
    reason TEXT not null,
    primary key (id)
);

create index idx_disputes_status on disputes (status);
create index idx_disputes_auction on disputes (auction_id);

alter table if exists disputes
    add constraint fk_dispute_auction foreign key (auction_id) references auctions;
alter table if exists disputes
    add constraint fk_dispute_raised_by foreign key (raised_by_id) references users;
