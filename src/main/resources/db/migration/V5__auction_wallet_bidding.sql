-- V5 — Core auction loop: auctions, wallet ledger (wallets + transactions + per-auction deposit
-- holds), and bids. UUID keys; FKs to users/listings. DDL generated from the JPA entities so
-- ddl-auto=validate matches.

create table wallets (
    available_balance numeric(14,2) not null,
    held_balance numeric(14,2) not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    user_id uuid not null unique,
    primary key (id)
);

create table auctions (
    base_price numeric(12,2) not null,
    current_highest_bid numeric(12,2),
    extension_count integer not null,
    created_at timestamp(6) not null,
    current_end_time timestamp(6) not null,
    end_time timestamp(6) not null,
    start_time timestamp(6) not null,
    updated_at timestamp(6) not null,
    version bigint not null,
    current_winner_id uuid,
    id uuid not null,
    listing_id uuid not null unique,
    status varchar(255) not null check ((status in ('SCHEDULED','OPEN','CLOSED','RESERVE_NOT_MET','CANCELLED'))),
    primary key (id)
);

create table bids (
    amount numeric(12,2) not null,
    created_at timestamp(6) not null,
    placed_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    auction_id uuid not null,
    bidder_id uuid not null,
    id uuid not null,
    primary key (id)
);

create table wallet_transactions (
    amount numeric(14,2) not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    wallet_id uuid not null,
    reference_type varchar(50),
    reference_id varchar(255),
    type varchar(255) not null check ((type in ('TOPUP','HOLD','RELEASE','CAPTURE','DEBIT','PAYOUT','WITHDRAWAL','REFUND'))),
    primary key (id)
);

create table bid_eligibility_holds (
    amount numeric(14,2) not null,
    created_at timestamp(6) not null,
    resolved_at timestamp(6),
    updated_at timestamp(6) not null,
    auction_id uuid not null,
    bidder_id uuid not null,
    id uuid not null,
    status varchar(255) not null check ((status in ('ACTIVE','RELEASED','CAPTURED'))),
    primary key (id),
    unique (auction_id, bidder_id)
);

create index idx_auctions_status on auctions (status);
create index idx_auctions_current_end_time on auctions (current_end_time);
create index idx_bids_auction_amount on bids (auction_id, amount);
create index idx_bids_auction_placed on bids (auction_id, placed_at);
create index idx_wallet_txn_wallet on wallet_transactions (wallet_id);
create index idx_wallet_txn_reference on wallet_transactions (reference_type, reference_id);
create index idx_hold_auction_status on bid_eligibility_holds (auction_id, status);

alter table if exists wallets
    add constraint fk_wallet_user foreign key (user_id) references users;
alter table if exists auctions
    add constraint fk_auction_listing foreign key (listing_id) references listings;
alter table if exists auctions
    add constraint fk_auction_winner foreign key (current_winner_id) references users;
alter table if exists bids
    add constraint fk_bid_auction foreign key (auction_id) references auctions;
alter table if exists bids
    add constraint fk_bid_bidder foreign key (bidder_id) references users;
alter table if exists wallet_transactions
    add constraint fk_wallettxn_wallet foreign key (wallet_id) references wallets;
alter table if exists bid_eligibility_holds
    add constraint fk_hold_auction foreign key (auction_id) references auctions;
alter table if exists bid_eligibility_holds
    add constraint fk_hold_bidder foreign key (bidder_id) references users;
