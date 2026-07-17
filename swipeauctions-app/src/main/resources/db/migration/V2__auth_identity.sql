-- V2 — Merge the org.vyntra auth/identity backend into the schema.
--
-- The V1 placeholder schema used BIGINT identity keys and a first-draft catalog/auction/wallet
-- layout. The merged auth model (User/Admin/OTP/KYC/sessions/reset-tokens) uses UUID primary keys,
-- which is incompatible with the V1 users table and its foreign keys. Since no data exists yet, we
-- drop the V1 tables and enum types and create the real identity schema here. The catalog / auction /
-- wallet tables will be rebuilt (with UUID user references) when that domain is implemented per plan.
--
-- The CREATE/ALTER statements below are generated verbatim from the JPA entities, so
-- spring.jpa.hibernate.ddl-auto=validate matches them exactly.

-- ---------- Drop the superseded V1 schema (all empty) ----------
DROP TABLE IF EXISTS
    notifications, dispute_cases, stripe_connect_accounts,
    wallet_transactions, wallets, bid_eligibility_holds, bids, auctions,
    listing_images, listing_attributes, listings, category_attribute_defs, categories,
    kyc_records, users
    CASCADE;

DROP TYPE IF EXISTS
    user_role, kyc_status, item_condition, attribute_value_type, listing_status,
    auction_status, wallet_txn_type, dispute_status, dispute_resolution,
    notification_channel, notification_status
    CASCADE;

-- ---------- Identity / auth schema (generated from JPA entities) ----------

create table admin_password_reset_tokens (
    used boolean,
    created_at timestamp(6) not null,
    expiry_time timestamp(6) not null,
    updated_at timestamp(6) not null,
    admin_id uuid not null unique,
    id uuid not null,
    token varchar(255) not null unique,
    primary key (id)
);

create table admin_sessions (
    active boolean,
    created_at timestamp(6) not null,
    last_activity_time timestamp(6),
    login_time timestamp(6),
    logout_time timestamp(6),
    updated_at timestamp(6) not null,
    admin_id uuid not null,
    id uuid not null,
    user_agent varchar(1000),
    device_id varchar(255) not null,
    device_name varchar(255),
    ip_address varchar(255),
    jwt_id varchar(255) not null unique,
    primary key (id)
);

create table admins (
    account_non_locked boolean,
    active boolean,
    failed_login_attempts integer,
    created_at timestamp(6) not null,
    last_login_at timestamp(6),
    locked_until timestamp(6),
    password_changed_at timestamp(6),
    updated_at timestamp(6) not null,
    id uuid not null,
    email varchar(255) not null unique,
    first_name varchar(255) not null,
    last_login_device varchar(255),
    last_login_ip varchar(255),
    last_name varchar(255) not null,
    mobile_number varchar(255) not null unique,
    password varchar(255) not null,
    role varchar(255) not null check ((role in ('USER','ADMIN','DEALER'))),
    primary key (id)
);

create table kyc_verifications (
    date_of_birth date,
    created_at timestamp(6) not null,
    submitted_at timestamp(6),
    updated_at timestamp(6) not null,
    verified_at timestamp(6),
    id uuid not null,
    user_id uuid not null unique,
    address varchar(1000),
    remarks varchar(1000),
    aadhaar_masked varchar(255),
    city varchar(255),
    full_name varchar(255) not null,
    gender varchar(255) check ((gender in ('MALE','FEMALE','OTHER'))),
    pan_number_masked varchar(255),
    pincode varchar(255),
    state varchar(255),
    status varchar(255) not null check ((status in ('NOT_SUBMITTED','PENDING','APPROVED','REJECTED'))),
    primary key (id)
);

create table otp_verifications (
    email_verified boolean,
    mobile_verified boolean,
    email_otp varchar(6),
    mobile_otp varchar(6),
    created_at timestamp(6) not null,
    email_otp_expiry timestamp(6),
    last_otp_sent_at timestamp(6),
    mobile_otp_expiry timestamp(6),
    updated_at timestamp(6) not null,
    id uuid not null,
    email varchar(255) not null unique,
    primary key (id)
);

create table user_password_reset_tokens (
    used boolean not null,
    created_at timestamp(6) not null,
    expiry_time timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    user_id uuid not null unique,
    token varchar(255) not null unique,
    primary key (id)
);

create table user_sessions (
    active boolean,
    created_at timestamp(6) not null,
    last_activity_time timestamp(6),
    login_time timestamp(6),
    logout_time timestamp(6),
    updated_at timestamp(6) not null,
    id uuid not null,
    user_id uuid not null,
    user_agent varchar(1000),
    device_id varchar(255) not null,
    device_name varchar(255),
    ip_address varchar(255),
    jwt_id varchar(255) not null unique,
    primary key (id)
);

create table user_update_requests (
    email_verified boolean,
    mobile_verified boolean,
    created_at timestamp(6) not null,
    email_otp_expiry timestamp(6),
    email_otp_sent_at timestamp(6),
    mobile_otp_expiry timestamp(6),
    mobile_otp_sent_at timestamp(6),
    requested_at timestamp(6),
    updated_at timestamp(6) not null,
    verified_at timestamp(6),
    id uuid not null,
    user_id uuid unique,
    email_otp varchar(255),
    mobile_otp varchar(255),
    new_email varchar(255),
    new_mobile_number varchar(255),
    primary key (id)
);

create table users (
    account_non_locked boolean,
    active boolean,
    email_verified boolean,
    failed_login_attempts integer,
    kyc_completed boolean,
    mobile_verified boolean,
    created_at timestamp(6) not null,
    last_login_at timestamp(6),
    locked_until timestamp(6),
    password_changed_at timestamp(6),
    updated_at timestamp(6) not null,
    id uuid not null,
    user_reference_number varchar(20) not null unique,
    email varchar(255) not null unique,
    kyc_status varchar(255) check ((kyc_status in ('NOT_SUBMITTED','PENDING','APPROVED','REJECTED'))),
    last_login_device varchar(255),
    last_login_ip varchar(255),
    mobile_number varchar(255) not null unique,
    password varchar(255) not null,
    role varchar(255) not null check ((role in ('USER','ADMIN','DEALER'))),
    primary key (id)
);

alter table if exists admin_password_reset_tokens
    add constraint fk_admin_pw_reset_admin foreign key (admin_id) references admins;

alter table if exists admin_sessions
    add constraint fk_admin_sessions_admin foreign key (admin_id) references admins;

alter table if exists kyc_verifications
    add constraint fk_kyc_user foreign key (user_id) references users;

alter table if exists user_password_reset_tokens
    add constraint fk_user_pw_reset_user foreign key (user_id) references users;

alter table if exists user_sessions
    add constraint fk_user_sessions_user foreign key (user_id) references users;

alter table if exists user_update_requests
    add constraint fk_user_update_user foreign key (user_id) references users;
