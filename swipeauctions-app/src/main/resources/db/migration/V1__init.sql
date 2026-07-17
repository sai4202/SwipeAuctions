-- SwipeAuctions — initial schema (general-item catalog: any category).
-- Flyway V1 baseline. Mirrors ../../../../../schema.sql (project reference DDL).

-- ========== ENUM TYPES ==========

CREATE TYPE user_role AS ENUM ('BIDDER', 'SELLER', 'ADMIN');
CREATE TYPE kyc_status AS ENUM ('PENDING', 'VERIFIED', 'REJECTED');
CREATE TYPE item_condition AS ENUM ('NEW', 'USED', 'REFURBISHED', 'FOR_PARTS');
CREATE TYPE attribute_value_type AS ENUM ('TEXT', 'NUMBER', 'BOOLEAN', 'ENUM');
CREATE TYPE listing_status AS ENUM ('DRAFT', 'PUBLISHED', 'WITHDRAWN');
CREATE TYPE auction_status AS ENUM ('SCHEDULED', 'OPEN', 'CLOSED', 'RESERVE_NOT_MET', 'CANCELLED');
CREATE TYPE wallet_txn_type AS ENUM ('TOPUP', 'HOLD', 'RELEASE', 'CAPTURE', 'DEBIT', 'PAYOUT', 'WITHDRAWAL', 'REFUND');
CREATE TYPE dispute_status AS ENUM ('OPEN', 'UNDER_REVIEW', 'RESOLVED');
CREATE TYPE dispute_resolution AS ENUM ('RELEASE_TO_SELLER', 'REFUND_TO_BUYER', 'PARTIAL');
CREATE TYPE notification_channel AS ENUM ('EMAIL', 'IN_APP');
CREATE TYPE notification_status AS ENUM ('PENDING', 'SENT', 'FAILED');

-- ========== IDENTITY ==========

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            user_role NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE kyc_records (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    provider                VARCHAR(50) NOT NULL DEFAULT 'STRIPE_IDENTITY',
    provider_reference_id   VARCHAR(255),
    status                  kyc_status NOT NULL DEFAULT 'PENDING',
    submitted_at            TIMESTAMPTZ,
    decided_at              TIMESTAMPTZ
);
CREATE INDEX idx_kyc_records_user_id ON kyc_records(user_id);

-- ========== CATALOG (any item category) ==========

CREATE TABLE categories (
    id           BIGSERIAL PRIMARY KEY,
    parent_id    BIGINT REFERENCES categories(id) ON DELETE RESTRICT,
    name         VARCHAR(100) NOT NULL,
    slug         VARCHAR(100) NOT NULL UNIQUE
);
CREATE INDEX idx_categories_parent_id ON categories(parent_id);

CREATE TABLE category_attribute_defs (
    id             BIGSERIAL PRIMARY KEY,
    category_id    BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    key            VARCHAR(100) NOT NULL,
    label          VARCHAR(150) NOT NULL,
    value_type     attribute_value_type NOT NULL,
    filterable     BOOLEAN NOT NULL DEFAULT true,
    sort_order     INT NOT NULL DEFAULT 0,
    UNIQUE (category_id, key)
);

CREATE TABLE listings (
    id                BIGSERIAL PRIMARY KEY,
    seller_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    category_id       BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    brand             VARCHAR(100),
    condition         item_condition NOT NULL,
    city              VARCHAR(100),
    state             VARCHAR(50),
    zip               VARCHAR(20),
    latitude          NUMERIC(9,6),
    longitude         NUMERIC(9,6),
    seller_notes      TEXT,
    reserve_price     NUMERIC(12,2) NOT NULL,
    status            listing_status NOT NULL DEFAULT 'DRAFT',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_listings_seller_id ON listings(seller_id);
CREATE INDEX idx_listings_category_id ON listings(category_id);
CREATE INDEX idx_listings_brand ON listings(brand);
CREATE INDEX idx_listings_price ON listings(reserve_price);
CREATE INDEX idx_listings_lat_lng ON listings(latitude, longitude);

CREATE TABLE listing_attributes (
    id           BIGSERIAL PRIMARY KEY,
    listing_id   BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    key          VARCHAR(100) NOT NULL,
    value        TEXT NOT NULL,
    UNIQUE (listing_id, key)
);
CREATE INDEX idx_listing_attributes_listing_id ON listing_attributes(listing_id);
CREATE INDEX idx_listing_attributes_key_value ON listing_attributes(key, value);

CREATE TABLE listing_images (
    id           BIGSERIAL PRIMARY KEY,
    listing_id   BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    url          VARCHAR(1000) NOT NULL,
    sort_order   INT NOT NULL DEFAULT 0,
    is_cover     BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_listing_images_listing_id ON listing_images(listing_id, sort_order);
CREATE UNIQUE INDEX uq_listing_images_one_cover ON listing_images(listing_id) WHERE is_cover;

-- ========== AUCTION + BIDDING ==========

CREATE TABLE auctions (
    id                    BIGSERIAL PRIMARY KEY,
    listing_id            BIGINT NOT NULL UNIQUE REFERENCES listings(id) ON DELETE RESTRICT,
    base_price            NUMERIC(12,2) NOT NULL,
    start_time            TIMESTAMPTZ NOT NULL,
    end_time              TIMESTAMPTZ NOT NULL,
    current_end_time      TIMESTAMPTZ NOT NULL,
    status                auction_status NOT NULL DEFAULT 'SCHEDULED',
    current_highest_bid   NUMERIC(12,2),
    current_winner_id     BIGINT REFERENCES users(id),
    extension_count       INT NOT NULL DEFAULT 0,
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_auctions_status ON auctions(status);
CREATE INDEX idx_auctions_current_end_time ON auctions(current_end_time);

CREATE TABLE bids (
    id           BIGSERIAL PRIMARY KEY,
    auction_id   BIGINT NOT NULL REFERENCES auctions(id) ON DELETE RESTRICT,
    bidder_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    amount       NUMERIC(12,2) NOT NULL,
    placed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_bids_auction_placed_at ON bids(auction_id, placed_at DESC);
CREATE INDEX idx_bids_auction_amount ON bids(auction_id, amount DESC);

CREATE TABLE bid_eligibility_holds (
    id            BIGSERIAL PRIMARY KEY,
    auction_id    BIGINT NOT NULL REFERENCES auctions(id) ON DELETE RESTRICT,
    bidder_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    amount        NUMERIC(12,2) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, RELEASED, CAPTURED
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at   TIMESTAMPTZ,
    UNIQUE (auction_id, bidder_id)
);
CREATE INDEX idx_bid_eligibility_holds_auction_status ON bid_eligibility_holds(auction_id, status);

-- ========== WALLET (internal ledger; only TOPUP and WITHDRAWAL rows touch Stripe) ==========

CREATE TABLE wallets (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    available_balance  NUMERIC(12,2) NOT NULL DEFAULT 0,
    held_balance       NUMERIC(12,2) NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE wallet_transactions (
    id               BIGSERIAL PRIMARY KEY,
    wallet_id        BIGINT NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    type             wallet_txn_type NOT NULL,
    amount           NUMERIC(12,2) NOT NULL,
    reference_type   VARCHAR(50),
    reference_id     VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_wallet_transactions_wallet_id ON wallet_transactions(wallet_id, created_at DESC);
CREATE INDEX idx_wallet_transactions_reference ON wallet_transactions(reference_type, reference_id);

-- ========== PAYMENT (Stripe Connect plumbing used by wallet.withdraw) ==========

CREATE TABLE stripe_connect_accounts (
    id                   BIGSERIAL PRIMARY KEY,
    seller_id            BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    stripe_account_id    VARCHAR(255) NOT NULL UNIQUE,
    onboarding_status    VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETE, RESTRICTED
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ========== DISPUTES ==========

CREATE TABLE dispute_cases (
    id             BIGSERIAL PRIMARY KEY,
    auction_id     BIGINT NOT NULL REFERENCES auctions(id) ON DELETE RESTRICT,
    opened_by      BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reason         TEXT NOT NULL,
    status         dispute_status NOT NULL DEFAULT 'OPEN',
    resolution     dispute_resolution,
    resolved_by    BIGINT REFERENCES users(id),
    resolved_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dispute_cases_auction_id ON dispute_cases(auction_id);
CREATE INDEX idx_dispute_cases_status ON dispute_cases(status);

-- ========== NOTIFICATIONS ==========

CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    type         VARCHAR(50) NOT NULL,
    channel      notification_channel NOT NULL,
    payload      JSONB,
    status       notification_status NOT NULL DEFAULT 'PENDING',
    sent_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_id ON notifications(user_id, created_at DESC);
