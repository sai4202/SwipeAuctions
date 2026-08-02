-- V23 — Admin audit log: who did what, when, on what. DDL generated from the AdminAuditLog entity
-- so ddl-auto=validate matches.

create table admin_audit_log (
    id uuid not null,
    admin_id uuid not null,
    action varchar(255) not null check (action in (
        'USER_SUSPENDED','USER_REACTIVATED','HOLD_RELEASED','AUCTION_FORCE_CLOSED','AUCTION_MODIFIED',
        'LISTING_REQUIRED_TIER_CHANGED','LISTING_CATEGORY_CHANGED','DISPUTE_RESOLVED','CATEGORY_CREATED',
        'CATEGORY_ATTRIBUTE_ADDED','KYC_APPROVED','KYC_REJECTED','STOCK_LISTING_CREATED','STOCK_BULK_IMPORTED',
        'REGISTRATION_FEE_UPDATED','SUBSCRIPTION_PRICES_UPDATED','MEMBERSHIP_BENEFIT_ADDED',
        'MEMBERSHIP_BENEFIT_TIERS_UPDATED','MEMBERSHIP_BENEFIT_REMOVED'
    )),
    target_type varchar(255) not null,
    target_id varchar(255),
    summary TEXT not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id)
);

create index idx_admin_audit_log_admin on admin_audit_log (admin_id);
create index idx_admin_audit_log_created_at on admin_audit_log (created_at);

alter table if exists admin_audit_log
    add constraint fk_admin_audit_log_admin foreign key (admin_id) references admins;
