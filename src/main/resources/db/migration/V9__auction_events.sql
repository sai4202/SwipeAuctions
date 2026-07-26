-- Seller-created auction events (grouping of auctions under one banner, e.g. a bank asset sale).
-- Currently only used for the Bank Assets category; auctions.event_id stays NULL for everything else.
CREATE TABLE auction_events (
    id            UUID PRIMARY KEY,
    seller_id     UUID NOT NULL REFERENCES users(id),
    name          VARCHAR(200) NOT NULL,
    location      VARCHAR(100),
    start_time    TIMESTAMP NOT NULL,
    closing_time  TIMESTAMP NOT NULL,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL
);

CREATE INDEX idx_auction_events_seller ON auction_events(seller_id);

ALTER TABLE auctions ADD COLUMN event_id UUID REFERENCES auction_events(id);
