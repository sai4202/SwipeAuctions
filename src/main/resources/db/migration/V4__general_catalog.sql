-- V4 — Pivot from the vehicle-specific auction model to a GENERAL, category-based catalog.
-- Drops the V3 vehicle/auction/yard/bid tables (empty) and creates the general catalog:
-- hierarchical categories + per-category attribute defs + listings (any item) + flexible
-- attributes + images. Auctions/wallet/bidding follow in a later migration.
-- DDL generated from the JPA entities so ddl-auto=validate matches.

-- ---------- Drop the superseded vehicle domain ----------
DROP TABLE IF EXISTS
    auction_event_vehicle_categories, auction_items, bids, auction_events,
    contact_persons, vehicle_details, vehicle_documents, vehicle_images, vehicles, yards
    CASCADE;

-- ---------- General catalog ----------

create table categories (
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    parent_id uuid,
    name varchar(100) not null,
    slug varchar(120) not null unique,
    primary key (id)
);

create table category_attribute_defs (
    filterable boolean not null,
    sort_order integer not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    category_id uuid not null,
    id uuid not null,
    attr_key varchar(100) not null,
    label varchar(150) not null,
    value_type varchar(255) not null check ((value_type in ('TEXT','NUMBER','BOOLEAN','ENUM'))),
    primary key (id),
    unique (category_id, attr_key)
);

create table listings (
    latitude numeric(9,6),
    longitude numeric(9,6),
    reserve_price numeric(12,2) not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    category_id uuid not null,
    id uuid not null,
    seller_id uuid not null,
    zip varchar(20),
    state varchar(50),
    brand varchar(100),
    city varchar(100),
    title varchar(200) not null,
    condition varchar(255) not null check ((condition in ('NEW','USED','REFURBISHED','FOR_PARTS'))),
    description TEXT,
    seller_notes TEXT,
    status varchar(255) not null check ((status in ('DRAFT','PUBLISHED','WITHDRAWN'))),
    primary key (id)
);

create table listing_attributes (
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    listing_id uuid not null,
    attr_key varchar(100) not null,
    attr_value TEXT not null,
    primary key (id),
    unique (listing_id, attr_key)
);

create table listing_images (
    is_cover boolean not null,
    sort_order integer not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    listing_id uuid not null,
    url varchar(1000) not null,
    primary key (id)
);

create index idx_listings_category on listings (category_id);
create index idx_listings_seller on listings (seller_id);
create index idx_listings_status on listings (status);
create index idx_listing_attr_key_value on listing_attributes (attr_key, attr_value);
create index idx_listing_images_listing on listing_images (listing_id, sort_order);

alter table if exists categories
    add constraint fk_category_parent foreign key (parent_id) references categories;
alter table if exists category_attribute_defs
    add constraint fk_catattr_category foreign key (category_id) references categories;
alter table if exists listings
    add constraint fk_listing_category foreign key (category_id) references categories;
alter table if exists listings
    add constraint fk_listing_seller foreign key (seller_id) references users;
alter table if exists listing_attributes
    add constraint fk_listingattr_listing foreign key (listing_id) references listings;
alter table if exists listing_images
    add constraint fk_listingimg_listing foreign key (listing_id) references listings;
