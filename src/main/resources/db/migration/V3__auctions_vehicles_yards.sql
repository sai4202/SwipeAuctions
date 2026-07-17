-- V3 — Auction / vehicle / yard / bid domain (the rest of the merged org.vyntra backend).
-- Tables generated verbatim from the JPA entities (UUID keys) so ddl-auto=validate matches.
-- The 9 identity/auth tables already exist from V2; this migration only adds the new tables.

create table auction_event_vehicle_categories (
    auction_event_id uuid not null,
    vehicle_category varchar(255) not null check ((vehicle_category in ('TWO_WHEELER','THREE_WHEELER','FOUR_WHEELER','COMMERCIAL_VEHICLE','CONSTRUCTION_EQUIPMENT'))),
    primary key (auction_event_id, vehicle_category)
);

create table auction_events (
    active boolean not null,
    display_order integer,
    featured boolean not null,
    published boolean not null,
    total_lots integer not null,
    created_at timestamp(6) not null,
    end_time timestamp(6) not null,
    start_time timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    event_code varchar(25) not null unique,
    city varchar(100) not null,
    region varchar(100),
    state varchar(100) not null,
    title varchar(150) not null,
    asset_category varchar(255) not null check ((asset_category in ('VEHICLE','PROPERTY','ELECTRONICS'))),
    auction_mode varchar(255) not null check ((auction_mode in ('TIMED','LIVE','HYBRID'))),
    auction_status varchar(255) not null check ((auction_status in ('DRAFT','UPCOMING','LIVE','CLOSED','CANCELLED','POSTPONED'))),
    banner_image varchar(255),
    description TEXT,
    provider_type varchar(255) not null check ((provider_type in ('SWIPE_AUCTIONS','BANK','INSURANCE','DEALER'))),
    terms_and_conditions TEXT,
    primary key (id)
);

create table auction_items (
    active boolean not null,
    bid_count integer not null,
    current_highest_bid numeric(19,2),
    minimum_increment numeric(19,2) not null,
    relist_count integer not null,
    relisted boolean not null,
    reserve_price numeric(19,2),
    starting_bid numeric(19,2) not null,
    watch_count integer not null,
    winning_amount numeric(19,2),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    auction_event_id uuid not null,
    id uuid not null,
    vehicle_id uuid not null,
    winner_id uuid,
    yard_id uuid not null,
    lot_number varchar(20) not null unique,
    status varchar(255) not null check ((status in ('UPCOMING','LIVE','SOLD','UNSOLD','WITHDRAWN','CANCELLED'))),
    primary key (id)
);

create table bids (
    bid_amount numeric(19,2) not null,
    bid_time timestamp(6) not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    auction_item_id uuid not null,
    bidder_id uuid not null,
    id uuid not null,
    bid_source varchar(30),
    ip_address varchar(50),
    device_information varchar(256),
    primary key (id)
);

create table contact_persons (
    active boolean not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    mobile_number varchar(15) not null,
    id uuid not null,
    yard_id uuid not null,
    designation varchar(100) not null,
    name varchar(100) not null,
    email varchar(150),
    primary key (id)
);

create table vehicle_details (
    accident_history boolean not null,
    fire_damaged boolean not null,
    flood_affected boolean not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    vehicle_id uuid not null unique,
    primary_damage varchar(100),
    secondary_damage varchar(100),
    battery_condition varchar(255) check ((battery_condition in ('EXCELLENT','GOOD','AVERAGE','POOR','DAMAGED'))),
    exterior_condition varchar(255) check ((exterior_condition in ('EXCELLENT','GOOD','AVERAGE','POOR','DAMAGED'))),
    inspection_remarks TEXT,
    interior_condition varchar(255) check ((interior_condition in ('EXCELLENT','GOOD','AVERAGE','POOR','DAMAGED'))),
    running_condition varchar(255) check ((running_condition in ('RUNNING','NOT_RUNNING'))),
    seller_remarks TEXT,
    start_condition varchar(255) check ((start_condition in ('STARTS','DOES_NOT_START'))),
    tyre_condition varchar(255) check ((tyre_condition in ('EXCELLENT','GOOD','AVERAGE','POOR','DAMAGED'))),
    primary key (id)
);

create table vehicle_documents (
    verified boolean not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    vehicle_id uuid not null,
    document_type varchar(255) not null check ((document_type in ('RC','INSURANCE_POLICY','FORM_35','NOC','PUC','TAX_RECEIPT','LOAN_CLOSURE_CERTIFICATE','AUCTION_CERTIFICATE','INSPECTION_REPORT'))),
    document_url varchar(255) not null,
    primary key (id)
);

create table vehicle_images (
    display_order integer not null,
    thumbnail boolean not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    vehicle_id uuid not null,
    image_url varchar(255) not null,
    primary key (id)
);

create table vehicles (
    auction_count integer not null,
    available_for_auction boolean not null,
    hypothecation boolean not null,
    key_available boolean not null,
    manufacturing_year integer,
    odometer_reading integer,
    ownership_number integer,
    rc_available boolean not null,
    registration_year integer,
    sold boolean not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    registration_number varchar(20),
    vehicle_reference_number varchar(20) not null unique,
    color varchar(50),
    body_type varchar(100),
    chassis_number varchar(100) unique,
    engine_number varchar(100) unique,
    make varchar(100) not null,
    model varchar(100) not null,
    variant varchar(100),
    asset_category varchar(255) not null check ((asset_category in ('VEHICLE','PROPERTY','ELECTRONICS'))),
    fuel_type varchar(255) check ((fuel_type in ('PETROL','DIESEL','CNG','LPG','ELECTRIC','HYBRID'))),
    transmission_type varchar(255) check ((transmission_type in ('MANUAL','AUTOMATIC'))),
    vehicle_category varchar(255) not null check ((vehicle_category in ('TWO_WHEELER','THREE_WHEELER','FOUR_WHEELER','COMMERCIAL_VEHICLE','CONSTRUCTION_EQUIPMENT'))),
    vehicle_type varchar(255) not null check ((vehicle_type in ('TWO_WHEELER','THREE_WHEELER','FOUR_WHEELER','COMMERCIAL_VEHICLE','CONSTRUCTION_EQUIPMENT'))),
    primary key (id)
);

create table yards (
    active boolean not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    pincode varchar(10) not null,
    contact_number varchar(15) not null,
    id uuid not null,
    yard_code varchar(20) not null unique,
    city varchar(100) not null,
    state varchar(100) not null,
    email varchar(150),
    name varchar(150) not null,
    address varchar(300) not null,
    primary key (id)
);

create index idx_event_code on auction_events (event_code);
create index idx_provider on auction_events (provider_type);
create index idx_asset_category on auction_events (asset_category);
create index idx_auction_status on auction_events (auction_status);
create index idx_start_time on auction_events (start_time);
create index idx_end_time on auction_events (end_time);
create index idx_lot_number on auction_items (lot_number);
create index idx_lot_status on auction_items (status);
create index idx_bid_amount on bids (bid_amount);
create index idx_bid_time on bids (bid_time);
create index idx_contact_mobile on contact_persons (mobile_number);
create index idx_vehicle_reference on vehicles (vehicle_reference_number);
create index idx_registration_number on vehicles (registration_number);
create index idx_chassis_number on vehicles (chassis_number);
create index idx_engine_number on vehicles (engine_number);
create index idx_yard_code on yards (yard_code);
create index idx_yard_city on yards (city);
create index idx_yard_state on yards (state);

alter table if exists auction_event_vehicle_categories
    add constraint fk_aevc_event foreign key (auction_event_id) references auction_events;
alter table if exists auction_items
    add constraint fk_item_event foreign key (auction_event_id) references auction_events;
alter table if exists auction_items
    add constraint fk_item_vehicle foreign key (vehicle_id) references vehicles;
alter table if exists auction_items
    add constraint fk_item_winner foreign key (winner_id) references users;
alter table if exists auction_items
    add constraint fk_item_yard foreign key (yard_id) references yards;
alter table if exists bids
    add constraint fk_bid_item foreign key (auction_item_id) references auction_items;
alter table if exists bids
    add constraint fk_bid_bidder foreign key (bidder_id) references users;
alter table if exists contact_persons
    add constraint fk_contact_yard foreign key (yard_id) references yards;
alter table if exists vehicle_details
    add constraint fk_vdetails_vehicle foreign key (vehicle_id) references vehicles;
alter table if exists vehicle_documents
    add constraint fk_vdocs_vehicle foreign key (vehicle_id) references vehicles;
alter table if exists vehicle_images
    add constraint fk_vimages_vehicle foreign key (vehicle_id) references vehicles;
