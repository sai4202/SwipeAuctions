-- V7 — Phase 2: real money via Stripe. Buyer wallet top-up (PaymentIntent), seller Connect payouts
-- (Transfer), and settlement remainder capture on auction close. DDL generated from the entities so
-- ddl-auto=validate matches.

alter table users
    add column stripe_customer_id varchar(255),
    add column stripe_account_id varchar(255),
    add column stripe_charges_enabled boolean not null default false,
    add column stripe_payouts_enabled boolean not null default false;

alter table auctions
    add column settlement_paid boolean not null default false;

create table wallet_topups (
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    completed_at timestamp(6),
    amount numeric(12,2) not null,
    id uuid not null,
    wallet_id uuid not null,
    stripe_payment_intent_id varchar(255) not null unique,
    status varchar(255) not null check ((status in ('PENDING','SUCCEEDED','FAILED'))),
    primary key (id)
);

create table wallet_withdrawals (
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    completed_at timestamp(6),
    amount numeric(12,2) not null,
    id uuid not null,
    wallet_id uuid not null,
    stripe_transfer_id varchar(255) unique,
    status varchar(255) not null check ((status in ('PENDING','SUCCEEDED','FAILED'))),
    primary key (id)
);

alter table if exists wallet_topups
    add constraint fk_wallet_topup_wallet foreign key (wallet_id) references wallets;
alter table if exists wallet_withdrawals
    add constraint fk_wallet_withdrawal_wallet foreign key (wallet_id) references wallets;

create index idx_wallet_topups_wallet on wallet_topups (wallet_id);
create index idx_wallet_withdrawals_wallet on wallet_withdrawals (wallet_id);
