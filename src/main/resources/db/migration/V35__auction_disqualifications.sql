-- Lets an admin ban a bidder from one specific auction (lighter-weight than the account-wide
-- suspend) — e.g. for a single mistaken/disputed bid. See AuctionDisqualification entity /
-- BidService#disqualifyBidder. DDL generated from the entity so ddl-auto=validate matches.

create table auction_disqualifications (
    id uuid not null,
    auction_id uuid not null,
    bidder_id uuid not null,
    admin_id uuid not null,
    reason varchar(500) not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id),
    unique (auction_id, bidder_id)
);

alter table if exists auction_disqualifications
    add constraint fk_disqualification_auction foreign key (auction_id) references auctions;

alter table if exists auction_disqualifications
    add constraint fk_disqualification_bidder foreign key (bidder_id) references users;

alter table if exists auction_disqualifications
    add constraint fk_disqualification_admin foreign key (admin_id) references admins;
