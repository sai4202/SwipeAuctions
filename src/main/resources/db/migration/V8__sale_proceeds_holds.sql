-- V8 — Seller proceeds escrow + dispute wallet-freeze. Sellers were never actually credited when
-- their auction sold; this adds that (into held balance, not available — auto-released after a
-- window unless a dispute is open, or released/refunded early by an admin resolving a dispute).

alter table wallet_transactions
    drop constraint wallet_transactions_type_check;
alter table wallet_transactions
    add constraint wallet_transactions_type_check
        check ((type in ('TOPUP','HOLD','RELEASE','CAPTURE','DEBIT','PAYOUT','WITHDRAWAL','REFUND','SALE_PROCEEDS')));

create table sale_proceeds_holds (
    amount numeric(14,2) not null,
    created_at timestamp(6) not null,
    resolved_at timestamp(6),
    updated_at timestamp(6) not null,
    auction_id uuid not null unique,
    id uuid not null,
    seller_id uuid not null,
    status varchar(255) not null check ((status in ('ACTIVE','RELEASED','REFUNDED'))),
    primary key (id)
);

create index idx_proceeds_status on sale_proceeds_holds (status);

alter table if exists sale_proceeds_holds
    add constraint fk_proceeds_auction foreign key (auction_id) references auctions;
alter table if exists sale_proceeds_holds
    add constraint fk_proceeds_seller foreign key (seller_id) references users;
